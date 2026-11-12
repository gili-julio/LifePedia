package com.lifepedia.api.integration;

import com.lifepedia.api.integration.IntegrationAccount.Status;
import com.lifepedia.api.integration.IntegrationException.Kind;
import com.lifepedia.api.integration.IntegrationSync.Outcome;
import com.lifepedia.api.integration.google.GoogleCalendarClient;
import com.lifepedia.api.integration.google.GoogleCalendarClient.Tokens;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.shared.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sincroniza as contas externas (sozinho pelo job ou na hora), guarda o histórico e avisa token recusado ou vencendo. */
@Service
public class IntegrationSyncService {

    static final int KEEP_HISTORY = 20;
    static final int MAX_PER_RUN = 200;
    static final int WARN_DAYS = 7;
    private static final Duration RETRY_FAILED = Duration.ofHours(6);
    private static final Duration SHORTEST = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(IntegrationSyncService.class);

    private final IntegrationAccountRepository accounts;
    private final IntegrationSyncRepository syncs;
    private final SecretBox secrets;
    private final GoogleCalendarClient google;
    private final List<AccountSyncer> syncers;
    private final NotificationService notifications;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    public IntegrationSyncService(IntegrationAccountRepository accounts, IntegrationSyncRepository syncs, SecretBox secrets,
                                  GoogleCalendarClient google, List<AccountSyncer> syncers, NotificationService notifications,
                                  Clock clock, PlatformTransactionManager transactions) {
        this.accounts = accounts;
        this.syncs = syncs;
        this.secrets = secrets;
        this.google = google;
        this.syncers = syncers;
        this.notifications = notifications;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    /** Sincroniza sem deixar exceção sair (conectar não falha porque a primeira busca falhou). */
    public void syncQuietly(UUID accountId, boolean manual) {
        try {
            sync(accountId, manual);
        } catch (RuntimeException e) {
            log.warn("Sincronização da conta {} falhou: {}", accountId, e.getClass().getSimpleName());
        }
    }

    /** Busca e grava; {@code false} se falhou ou se a conta já estava sincronizando. */
    public boolean sync(UUID accountId, boolean manual) {
        if (!running.add(accountId)) return false;
        long started = System.nanoTime();
        try {
            IntegrationAccount account = transaction.execute(status -> accounts.findById(accountId).orElse(null));
            if (account == null) return false;
            try {
                Connection connection = connect(account);
                int items = syncer(account.getProvider()).sync(connection);
                finish(accountId, manual, items, null, started);
                return true;
            } catch (IntegrationException e) {
                finish(accountId, manual, 0, e, started);
                return false;
            }
        } finally {
            running.remove(accountId);
        }
    }

    /** Conta da pessoa pronta para chamar o serviço (renova o acesso do Google se precisar). */
    public Connection connection(UUID userId, UUID accountId) throws IntegrationException {
        IntegrationAccount account = transaction.execute(status -> accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada.")));
        return connect(account);
    }

    /** Contas na vez de sincronizar (cada serviço no seu intervalo; em erro, a cada 6 h). Devolve quantas rodaram. */
    public int syncDue() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<UUID> due = accounts.dueBefore(now.minus(SHORTEST), now.minus(RETRY_FAILED), Status.ACTIVE);
        int count = 0;
        for (UUID id : due.subList(0, Math.min(due.size(), MAX_PER_RUN))) {
            IntegrationAccount account = transaction.execute(status -> accounts.findById(id).orElse(null));
            if (account == null) continue;
            boolean ready = account.getLastSyncAt() == null || account.getStatus() != Status.ACTIVE
                    || account.getLastSyncAt().isBefore(now.minus(account.getProvider().interval()));
            if (!ready) continue;
            syncQuietly(id, false);
            count++;
        }
        return count;
    }

    /** Avisa (uma vez por token) quem tem token pessoal vencendo nos próximos 7 dias. */
    public int warnExpiring() {
        LocalDate today = LocalDate.now(clock);
        Integer warned = transaction.execute(status -> {
            List<IntegrationAccount> list = accounts.expiring(today.plusDays(WARN_DAYS), Status.ACTIVE);
            for (IntegrationAccount account : list) {
                account.setExpiryWarnedOn(today);
                notifications.notify(account.getUserId(), NotificationType.INTEGRATION_EXPIRING, null, null, Map.of(
                        "provider", account.getProvider().name(), "nickname", account.getNickname(),
                        "accountId", account.getId().toString(), "date", account.getTokenExpiresAt().toString()));
            }
            return list.size();
        });
        return warned == null ? 0 : warned;
    }

    private Connection connect(IntegrationAccount account) throws IntegrationException {
        IntegrationProvider provider = account.getProvider();
        String token = null;
        if (provider.usesToken()) {
            if (!secrets.enabled()) throw new IntegrationException(Kind.UNAVAILABLE, "O servidor está sem a chave de cifra dos tokens.");
            token = provider == IntegrationProvider.GOOGLE ? googleAccess(account) : secrets.open(account.getAccessToken());
            if (token == null) throw new IntegrationException(Kind.AUTH, "Não deu para ler o token salvo. Conecte de novo.");
        }
        return new Connection(account.getId(), account.getUserId(), provider, account.getNickname(), account.getBaseUrl(),
                account.getExternalId(), account.getLogin(), token, account.isCanWrite());
    }

    private String googleAccess(IntegrationAccount account) throws IntegrationException {
        String access = secrets.open(account.getAccessToken());
        OffsetDateTime expires = account.getAccessExpiresAt();
        if (access != null && expires != null && expires.isAfter(OffsetDateTime.now(clock).plusMinutes(2))) return access;
        String refresh = secrets.open(account.getRefreshToken());
        if (refresh == null) throw new IntegrationException(Kind.AUTH, "O Google encerrou o acesso à agenda. Conecte de novo.");
        Tokens tokens = google.refresh(refresh);
        OffsetDateTime until = OffsetDateTime.now(clock).plusSeconds(tokens.expiresIn());
        transaction.executeWithoutResult(status -> accounts.findById(account.getId())
                .ifPresent(a -> a.setAccess(secrets.seal(tokens.accessToken()), until)));
        return tokens.accessToken();
    }

    private AccountSyncer syncer(IntegrationProvider provider) throws IntegrationException {
        return syncers.stream().filter(s -> s.supports(provider)).findFirst()
                .orElseThrow(() -> new IntegrationException(Kind.UNAVAILABLE, "Sincronização indisponível."));
    }

    private void finish(UUID accountId, boolean manual, int items, IntegrationException failure, long started) {
        long ms = (System.nanoTime() - started) / 1_000_000;
        OffsetDateTime now = OffsetDateTime.now(clock);
        transaction.executeWithoutResult(status -> accounts.findById(accountId).ifPresent(account -> {
            if (failure == null) {
                account.synced(now);
                syncs.save(new IntegrationSync(accountId, Outcome.SUCCESS, manual, items, null, ms));
            } else {
                boolean revoked = failure.kind() == Kind.AUTH;
                boolean wasActive = account.getStatus() == Status.ACTIVE;
                account.failed(now, failure.getMessage(), revoked);
                syncs.save(new IntegrationSync(accountId, Outcome.FAILED, manual, 0, failure.getMessage(), ms));
                if (revoked && wasActive) {
                    notifications.notify(account.getUserId(), NotificationType.INTEGRATION_FAILED, null, null, Map.of(
                            "provider", account.getProvider().name(), "nickname", account.getNickname(),
                            "accountId", accountId.toString()));
                }
            }
            syncs.flush();
            syncs.trim(accountId, KEEP_HISTORY);
        }));
    }
}
