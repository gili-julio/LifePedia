package com.lifepedia.api.dev;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DevRepoRepository extends JpaRepository<DevRepo, UUID> {

    List<DevRepo> findByUserIdOrderByCreatedAtAsc(UUID userId);

    List<DevRepo> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    List<DevRepo> findByAccountId(UUID accountId);

    Optional<DevRepo> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByProjectIdAndAccountIdAndExternalId(UUID projectId, UUID accountId, String externalId);

    long countByProjectId(UUID projectId);
}
