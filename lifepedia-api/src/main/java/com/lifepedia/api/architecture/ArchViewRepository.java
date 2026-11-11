package com.lifepedia.api.architecture;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArchViewRepository extends JpaRepository<ArchView, UUID> {

    List<ArchView> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    Optional<ArchView> findByIdAndProjectId(UUID id, UUID projectId);

    long countByProjectId(UUID projectId);
}
