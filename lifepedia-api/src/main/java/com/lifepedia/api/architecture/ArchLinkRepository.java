package com.lifepedia.api.architecture;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArchLinkRepository extends JpaRepository<ArchLink, UUID> {

    Optional<ArchLink> findByToken(String token);

    Optional<ArchLink> findByIdAndProjectId(UUID id, UUID projectId);

    List<ArchLink> findByProjectIdAndExpiresAtAfterOrderByCreatedAtDesc(UUID projectId, OffsetDateTime now);

    long countByProjectIdAndExpiresAtAfter(UUID projectId, OffsetDateTime now);

    void deleteByExpiresAtBefore(OffsetDateTime before);
}
