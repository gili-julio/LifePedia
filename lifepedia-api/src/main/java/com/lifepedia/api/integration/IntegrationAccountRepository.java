package com.lifepedia.api.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationAccountRepository extends JpaRepository<IntegrationAccount, UUID> {

    List<IntegrationAccount> findByUserIdOrderByCreatedAtAsc(UUID userId);

    List<IntegrationAccount> findByUserIdAndProviderOrderByCreatedAtAsc(UUID userId, IntegrationProvider provider);

    Optional<IntegrationAccount> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Query("""
            select a from IntegrationAccount a where a.userId = :userId and a.provider = :provider
              and coalesce(a.baseUrl, '') = coalesce(:baseUrl, '') and a.externalId = :externalId
            """)
    Optional<IntegrationAccount> findSame(UUID userId, IntegrationProvider provider, String baseUrl, String externalId);

    /** Ids das contas na vez de sincronizar: ativas sem sincronizar desde {@code before} ou em erro há mais de {@code retry}. */
    @Query("""
            select a.id from IntegrationAccount a
             where (a.status = :active and (a.lastSyncAt is null or a.lastSyncAt < :before))
                or (a.status <> :active and a.lastSyncAt < :retry)
             order by a.lastSyncAt asc nulls first
            """)
    List<UUID> dueBefore(OffsetDateTime before, OffsetDateTime retry, IntegrationAccount.Status active);

    /** Tokens pessoais que vencem até {@code limit} e ainda não foram avisados (o aviso zera ao trocar o token). */
    @Query("""
            select a from IntegrationAccount a where a.tokenExpiresAt is not null and a.tokenExpiresAt <= :limit
               and a.status = :active and a.expiryWarnedOn is null
            """)
    List<IntegrationAccount> expiring(LocalDate limit, IntegrationAccount.Status active);
}
