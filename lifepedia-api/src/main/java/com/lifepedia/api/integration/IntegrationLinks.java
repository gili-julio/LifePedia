package com.lifepedia.api.integration;

import com.lifepedia.api.integration.IntegrationDtos.DuolingoStats;
import com.lifepedia.api.integration.IntegrationDtos.EventResponse;
import com.lifepedia.api.integration.code.CodeHost;
import com.lifepedia.api.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** O que outros módulos (Dev, conquistas) usam das contas externas, sem mexer nas tabelas daqui. */
@Service
public class IntegrationLinks {

    /** Conta para escolher num formulário (sem token). */
    public record LinkedAccount(UUID id, IntegrationProvider provider, String nickname, String color, String login, boolean active,
                                boolean canWrite) {
    }

    private final IntegrationAccountRepository accounts;
    private final IntegrationSyncService sync;
    private final IntegrationService service;
    private final List<CodeHost> codeHosts;
    private final Clock clock;

    public IntegrationLinks(IntegrationAccountRepository accounts, IntegrationSyncService sync, IntegrationService service,
                            List<CodeHost> codeHosts, Clock clock) {
        this.accounts = accounts;
        this.sync = sync;
        this.service = service;
        this.codeHosts = codeHosts;
        this.clock = clock;
    }

    /** Conta da pessoa (404 se não for dela). */
    @Transactional(readOnly = true)
    public LinkedAccount account(UUID userId, UUID accountId) {
        return accounts.findByIdAndUserId(accountId, userId).map(IntegrationLinks::linked)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada."));
    }

    @Transactional(readOnly = true)
    public Optional<LinkedAccount> find(UUID userId, UUID accountId) {
        return accounts.findByIdAndUserId(accountId, userId).map(IntegrationLinks::linked);
    }

    @Transactional(readOnly = true)
    public List<LinkedAccount> accounts(UUID userId) {
        return accounts.findByUserIdOrderByCreatedAtAsc(userId).stream().map(IntegrationLinks::linked).toList();
    }

    /** Conta pronta para chamar o serviço; fora de transação (pode renovar o token). */
    public Connection connection(UUID userId, UUID accountId) throws IntegrationException {
        return sync.connection(userId, accountId);
    }

    /** Sincroniza a conta agora (ex.: repositório novo ligado); falha só fica no histórico. */
    public void syncNow(UUID accountId) {
        sync.syncQuietly(accountId, true);
    }

    public CodeHost codeHost(IntegrationProvider provider) {
        return codeHosts.stream().filter(h -> h.provider() == provider).findFirst()
                .orElseThrow(() -> new NotFoundException("Serviço não encontrado."));
    }

    public List<EventResponse> calendar(UUID userId, LocalDate from, LocalDate to) {
        return service.calendar(userId, from, to);
    }

    /** Ofensiva e XP do Duolingo ligado (vazio se não houver). */
    @Transactional(readOnly = true)
    public Optional<DuolingoStats> duolingo(UUID userId) {
        return accounts.findByUserIdAndProviderOrderByCreatedAtAsc(userId, IntegrationProvider.DUOLINGO).stream().findFirst()
                .map(a -> DuolingoSyncer.stats(a.getStats(), LocalDate.now(clock)));
    }

    private static LinkedAccount linked(IntegrationAccount a) {
        return new LinkedAccount(a.getId(), a.getProvider(), a.getNickname(), a.getColor(), a.getLogin(),
                a.getStatus() == IntegrationAccount.Status.ACTIVE, a.isCanWrite());
    }
}
