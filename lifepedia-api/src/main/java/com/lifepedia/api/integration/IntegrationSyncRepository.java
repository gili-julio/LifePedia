package com.lifepedia.api.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface IntegrationSyncRepository extends JpaRepository<IntegrationSync, UUID> {

    List<IntegrationSync> findTop20ByAccountIdOrderByCreatedAtDesc(UUID accountId);

    /** Mantém só as {@code keep} mais novas da conta. */
    @Modifying
    @Query(value = """
            delete from integration_syncs where account_id = :accountId and id not in (
              select id from integration_syncs where account_id = :accountId order by created_at desc limit :keep)
            """, nativeQuery = true)
    void trim(UUID accountId, int keep);
}
