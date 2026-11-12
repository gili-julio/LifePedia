package com.lifepedia.api.integration;

import com.lifepedia.api.config.CorsProperties;
import com.lifepedia.api.config.IntegrationProperties;
import com.lifepedia.api.integration.IntegrationAccount.Status;
import com.lifepedia.api.integration.IntegrationDtos.AccountExport;
import com.lifepedia.api.integration.IntegrationDtos.AccountResponse;
import com.lifepedia.api.integration.IntegrationDtos.DuolingoRequest;
import com.lifepedia.api.integration.IntegrationDtos.DuolingoStats;
import com.lifepedia.api.integration.IntegrationDtos.EventResponse;
import com.lifepedia.api.integration.IntegrationDtos.GoogleStart;
import com.lifepedia.api.integration.IntegrationDtos.LabelRequest;
import com.lifepedia.api.integration.IntegrationDtos.Overview;
import com.lifepedia.api.integration.IntegrationDtos.ProviderInfo;
import com.lifepedia.api.integration.IntegrationDtos.ReconnectRequest;
import com.lifepedia.api.integration.IntegrationDtos.Summary;
import com.lifepedia.api.integration.IntegrationDtos.SyncEntry;
import com.lifepedia.api.integration.IntegrationDtos.TokenRequest;
import com.lifepedia.api.integration.code.CodeHost;
import com.lifepedia.api.integration.code.CodeHost.Identity;
import com.lifepedia.api.integration.duolingo.DuolingoClient;
import com.lifepedia.api.integration.google.GoogleCalendarClient;
import com.lifepedia.api.integration.google.GoogleCalendarClient.Tokens;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.shared.net.SafeHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Central de integrações: ligar, renomear, reconectar e desligar contas externas, e a agenda do Google. */
@Service
public class IntegrationService {

    static final int MAX_ACCOUNTS = 20;
    static final int MAX_CALENDAR_DAYS = 62;
    private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final String DEFAULT_COLOR = "#6d28d9";
    private static final String GITLAB_COM = "https://gitlab.com";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IntegrationAccountRepository accounts;
    private final IntegrationSyncRepository syncs;
    private final CalendarEventRepository events;
    private final List<CodeHost> codeHosts;
    private final List<AccountSyncer> syncers;
    private final GoogleCalendarClient google;
    private final DuolingoClient duolingo;
    private final SecretBox secrets;
    private final IntegrationSyncService sync;
    private final IntegrationProperties properties;
    private final CorsProperties cors;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public IntegrationService(IntegrationAccountRepository accounts, IntegrationSyncRepository syncs, CalendarEventRepository events,
                              List<CodeHost> codeHosts, List<AccountSyncer> syncers, GoogleCalendarClient google,
                              DuolingoClient duolingo, SecretBox secrets, IntegrationSyncService sync,
                              IntegrationProperties properties, CorsProperties cors, RateLimiter rateLimiter, Clock clock,
                              PlatformTransactionManager transactions) {
        this.accounts = accounts;
        this.syncs = syncs;
        this.events = events;
        this.codeHosts = codeHosts;
        this.syncers = syncers;
        this.google = google;
        this.duolingo = duolingo;
        this.secrets = secrets;
        this.sync = sync;
        this.properties = properties;
        this.cors = cors;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID userId) {
        List<ProviderInfo> providers = Arrays.stream(IntegrationProvider.values())
                .map(p -> new ProviderInfo(p, unavailable(p) == null, unavailable(p))).toList();
        return new Overview(providers, responses(userId));
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID userId) {
        List<IntegrationAccount> mine = accounts.findByUserIdOrderByCreatedAtAsc(userId);
        int problems = (int) mine.stream().filter(a -> a.getStatus() == Status.ERROR).count();
        DuolingoStats stats = mine.stream().filter(a -> a.getProvider() == IntegrationProvider.DUOLINGO).findFirst()
                .map(a -> DuolingoSyncer.stats(a.getStats(), today())).orElse(null);
        return new Summary(mine.size(), problems, stats);
    }

    /** GitLab ou GitHub com token pessoal; o mesmo usuário no mesmo servidor só troca o token. */
    public AccountResponse connectToken(UUID userId, IntegrationProvider provider, TokenRequest request) {
        if (!provider.isCodeHost()) throw new NotFoundException("Serviço não encontrado.");
        requireAvailable(provider);
        requireAttempt(userId);
        String baseUrl = provider == IntegrationProvider.GITLAB ? gitlabBase(request.baseUrl()) : null;
        String token = request.token().trim();
        Identity identity;
        try {
            identity = host(provider).identify(baseUrl, token);
        } catch (IntegrationException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, e.kind() == IntegrationException.Kind.AUTH
                    ? "Token recusado. Confira se copiou inteiro e se ainda vale." : e.getMessage());
        }
        UUID id = transaction.execute(status -> {
            IntegrationAccount account = accounts.findSame(userId, provider, baseUrl, identity.externalId()).orElse(null);
            if (account == null) {
                requireRoom(userId);
                account = new IntegrationAccount(userId, provider, baseUrl, identity.externalId());
                account.setLabel(nickname(request.nickname(), provider == IntegrationProvider.GITLAB ? "GitLab" : "GitHub"),
                        color(request.color()));
            }
            account.setCredentials(identity.login(), secrets.seal(token), null, identity.tokenExpiresAt(), identity.canWrite());
            return accounts.save(account).getId();
        });
        sync.syncQuietly(id, true);
        return response(userId, id);
    }

    public AccountResponse connectDuolingo(UUID userId, DuolingoRequest request) {
        requireAttempt(userId);
        DuolingoClient.Profile profile;
        try {
            profile = duolingo.profile(request.username());
        } catch (IntegrationException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        UUID id = transaction.execute(status -> {
            List<IntegrationAccount> existing = accounts.findByUserIdAndProviderOrderByCreatedAtAsc(userId, IntegrationProvider.DUOLINGO);
            if (!existing.isEmpty() && !existing.getFirst().getExternalId().equals(profile.id())) {
                throw new BusinessException(HttpStatus.CONFLICT, "Já existe um Duolingo ligado. Desligue antes de trocar.");
            }
            IntegrationAccount account = existing.isEmpty() ? null : existing.getFirst();
            if (account == null) {
                requireRoom(userId);
                account = new IntegrationAccount(userId, IntegrationProvider.DUOLINGO, null, profile.id());
                account.setLabel(nickname(request.nickname(), "Duolingo"), color(request.color() == null ? "#58cc02" : request.color()));
            }
            account.setCredentials(profile.username(), null, null, null, false);
            return accounts.save(account).getId();
        });
        sync.syncQuietly(id, true);
        return response(userId, id);
    }

    /** Endereço do consentimento do Google; o {@code state} cifrado amarra a volta a esta pessoa por 10 minutos. */
    public GoogleStart googleStart(UUID userId) {
        requireAvailable(IntegrationProvider.GOOGLE);
        requireAttempt(userId);
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        String state = secrets.seal(userId + "|" + clock.instant().getEpochSecond() + "|" + HexFormat.of().formatHex(nonce));
        return new GoogleStart(google.authorizeUrl(state, redirectUri()).toString());
    }

    /** Volta do Google; devolve o caminho do site para onde levar a pessoa (com o resultado na query). */
    public String googleCallback(UUID userId, String code, String state, String error) {
        if (error != null || code == null || state == null || !validState(userId, state)) {
            return "/integracoes?erro=" + (error != null ? "google-cancelado" : "google");
        }
        Tokens tokens;
        try {
            tokens = google.exchange(code, redirectUri());
        } catch (IntegrationException e) {
            log.info("Troca do código do Google falhou: {}", e.kind());
            return "/integracoes?erro=google";
        }
        if (!tokens.calendarGranted()) return "/integracoes?erro=google-permissao";
        UUID id = transaction.execute(status -> {
            IntegrationAccount account = accounts.findSame(userId, IntegrationProvider.GOOGLE, null, tokens.subject()).orElse(null);
            if (account == null) {
                if (tokens.refreshToken() == null || accounts.countByUserId(userId) >= MAX_ACCOUNTS) return null;
                account = new IntegrationAccount(userId, IntegrationProvider.GOOGLE, null, tokens.subject());
                account.setLabel(nickname(tokens.email(), "Google"), DEFAULT_COLOR);
            }
            String login = tokens.email() == null ? tokens.subject() : tokens.email();
            account.setCredentials(login, secrets.seal(tokens.accessToken()), secrets.seal(tokens.refreshToken()), null, false);
            account.setAccess(secrets.seal(tokens.accessToken()), OffsetDateTime.now(clock).plusSeconds(tokens.expiresIn()));
            return accounts.save(account).getId();
        });
        if (id == null) return "/integracoes?erro=google-limite";
        sync.syncQuietly(id, true);
        return "/integracoes?conectado=" + id;
    }

    @Transactional
    public AccountResponse label(UUID userId, UUID id, LabelRequest request) {
        IntegrationAccount account = find(userId, id);
        account.setLabel(request.nickname().trim(), request.color().toLowerCase(Locale.ROOT));
        return response(userId, account);
    }

    /** Token novo para a mesma conta (precisa ser da mesma pessoa no serviço). */
    public AccountResponse reconnect(UUID userId, UUID id, ReconnectRequest request) {
        IntegrationAccount account = transaction.execute(status -> find(userId, id));
        if (!account.getProvider().isCodeHost()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Esta conta reconecta pelo botão do serviço.");
        }
        requireAvailable(account.getProvider());
        requireAttempt(userId);
        String token = request.token().trim();
        Identity identity;
        try {
            identity = host(account.getProvider()).identify(account.getBaseUrl(), token);
        } catch (IntegrationException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (!identity.externalId().equals(account.getExternalId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Esse token é de outra conta (" + identity.login() + ").");
        }
        transaction.executeWithoutResult(status -> find(userId, id)
                .setCredentials(identity.login(), secrets.seal(token), null, identity.tokenExpiresAt(), identity.canWrite()));
        sync.syncQuietly(id, true);
        return response(userId, id);
    }

    /** Sincroniza agora (no máximo 1 vez por minuto por conta). */
    public AccountResponse syncNow(UUID userId, UUID id) {
        transaction.execute(status -> find(userId, id));
        if (!rateLimiter.tryAcquire("integration-sync:" + id, 1, Duration.ofMinutes(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Acabou de sincronizar. Espere um minuto.");
        }
        sync.syncQuietly(id, true);
        return response(userId, id);
    }

    @Transactional(readOnly = true)
    public List<SyncEntry> history(UUID userId, UUID id) {
        find(userId, id);
        return syncs.findTop20ByAccountIdOrderByCreatedAtDesc(id).stream()
                .map(s -> new SyncEntry(s.getCreatedAt(), s.getOutcome(), s.isManual(), s.getItems(), s.getMessage(), s.getDurationMs()))
                .toList();
    }

    /** Desliga a conta: o que veio dela (eventos, repositórios do Dev, MRs e issues) sai junto pelo banco. */
    public void disconnect(UUID userId, UUID id) {
        IntegrationAccount account = transaction.execute(status -> {
            IntegrationAccount found = find(userId, id);
            accounts.delete(found);
            return found;
        });
        if (account.getProvider() == IntegrationProvider.GOOGLE) {
            String refresh = secrets.open(account.getRefreshToken());
            if (refresh != null) google.revoke(refresh);
        }
    }

    /** Eventos do Google Agenda de todas as contas da pessoa no intervalo (até 62 dias). */
    @Transactional(readOnly = true)
    public List<EventResponse> calendar(UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_CALENDAR_DAYS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Intervalo de até " + MAX_CALENDAR_DAYS + " dias.");
        }
        Map<UUID, IntegrationAccount> mine = accounts.findByUserIdAndProviderOrderByCreatedAtAsc(userId, IntegrationProvider.GOOGLE)
                .stream().collect(Collectors.toMap(IntegrationAccount::getId, a -> a));
        if (mine.isEmpty()) return List.of();
        OffsetDateTime start = from.atStartOfDay(clock.getZone()).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime();
        List<EventResponse> list = new ArrayList<>();
        for (CalendarEvent event : events.between(userId, start, end)) {
            IntegrationAccount account = mine.get(event.getAccountId());
            if (account == null) continue;
            list.add(new EventResponse(event.getId(), account.getId(), account.getNickname(), account.getColor(), event.getTitle(),
                    event.getStartsAt(), event.getEndsAt(), event.isAllDay(), event.getLocation(), event.getMeetUrl(),
                    event.getHtmlUrl()));
        }
        return list;
    }

    /** Registro para o arquivo da conta: só o que foi ligado, nunca token. */
    @Transactional(readOnly = true)
    public List<AccountExport> exportData(UUID userId) {
        return accounts.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(a -> new AccountExport(a.getProvider(), a.getNickname(), a.getLogin(), a.getBaseUrl(), a.getCreatedAt()))
                .toList();
    }

    /** {@code https://servidor[/caminho]} do GitLab (vazio = gitlab.com); http e endereço interno não passam. */
    static String gitlabBase(String raw) {
        if (raw == null || raw.isBlank()) return GITLAB_COM;
        URI uri;
        try {
            uri = SafeHttpClient.parseUserUrl(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Endereço do GitLab inválido.");
        }
        if (!"https".equals(uri.getScheme()) || uri.getPort() != -1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Use o endereço https do GitLab.");
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "").replaceAll("/api/v4$", "");
        return "https://" + uri.getHost() + path;
    }

    private AccountResponse response(UUID userId, UUID id) {
        return transaction.execute(status -> response(userId, find(userId, id)));
    }

    private AccountResponse response(UUID userId, IntegrationAccount account) {
        return response(account, usage(userId).getOrDefault(account.getId(), List.of()));
    }

    private List<AccountResponse> responses(UUID userId) {
        Map<UUID, List<String>> usage = usage(userId);
        return accounts.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(a -> response(a, usage.getOrDefault(a.getId(), List.of()))).toList();
    }

    private AccountResponse response(IntegrationAccount a, List<String> projects) {
        DuolingoStats stats = a.getProvider() == IntegrationProvider.DUOLINGO ? DuolingoSyncer.stats(a.getStats(), today()) : null;
        return new AccountResponse(a.getId(), a.getProvider(), a.getNickname(), a.getColor(), a.getBaseUrl(), a.getLogin(),
                a.getStatus(), a.getLastSyncAt(), a.getLastError(), a.getTokenExpiresAt(), a.isCanWrite(), stats, projects,
                a.getCreatedAt());
    }

    private Map<UUID, List<String>> usage(UUID userId) {
        Map<UUID, List<String>> all = new HashMap<>();
        for (AccountSyncer syncer : syncers) {
            syncer.usage(userId).forEach((id, names) -> all.computeIfAbsent(id, k -> new ArrayList<>()).addAll(names));
        }
        return all;
    }

    private boolean validState(UUID userId, String state) {
        String plain = secrets.open(state);
        if (plain == null) return false;
        String[] parts = plain.split("\\|");
        if (parts.length != 3 || !parts[0].equals(userId.toString())) return false;
        try {
            long at = Long.parseLong(parts[1]);
            return clock.instant().getEpochSecond() - at <= STATE_TTL.toSeconds();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String redirectUri() {
        String base = properties.appUrl() != null && !properties.appUrl().isBlank() ? properties.appUrl()
                : cors.allowedOrigins().getFirst();
        return base.replaceAll("/+$", "") + "/api/integrations/google/callback";
    }

    private String unavailable(IntegrationProvider provider) {
        if (provider.usesToken() && !secrets.enabled()) return "O servidor ainda não tem a chave de cifra dos tokens (INTEGRATIONS_KEY).";
        if (provider == IntegrationProvider.GOOGLE && !google.enabled()) {
            return "O servidor ainda não tem o login com Google e o segredo dele (GOOGLE_CLIENT_SECRET).";
        }
        return null;
    }

    private void requireAvailable(IntegrationProvider provider) {
        String reason = unavailable(provider);
        if (reason != null) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, reason);
    }

    private void requireAttempt(UUID userId) {
        if (!rateLimiter.tryAcquire("integration-connect:" + userId, 15, Duration.ofHours(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas de conectar. Tente de novo mais tarde.");
        }
    }

    private void requireRoom(UUID userId) {
        if (accounts.countByUserId(userId) >= MAX_ACCOUNTS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_ACCOUNTS + " contas ligadas.");
        }
    }

    private CodeHost host(IntegrationProvider provider) {
        return codeHosts.stream().filter(h -> h.provider() == provider).findFirst()
                .orElseThrow(() -> new NotFoundException("Serviço não encontrado."));
    }

    private IntegrationAccount find(UUID userId, UUID id) {
        return accounts.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Conta não encontrada."));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String nickname(String value, String fallback) {
        String name = value == null || value.isBlank() ? fallback : value.trim();
        return name.length() > 40 ? name.substring(0, 40) : name;
    }

    private static String color(String value) {
        return value == null ? DEFAULT_COLOR : value.toLowerCase(Locale.ROOT);
    }
}
