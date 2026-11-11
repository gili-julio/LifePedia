package com.lifepedia.api.architecture;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArchShareRepository extends JpaRepository<ArchShare, UUID> {

    List<ArchShare> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    Optional<ArchShare> findByProjectIdAndUserId(UUID projectId, UUID userId);

    Optional<ArchShare> findByProjectIdAndGroupId(UUID projectId, UUID groupId);

    long countByProjectId(UUID projectId);
}
