package com.lifepedia.api.architecture;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArchLightRepository extends JpaRepository<ArchLight, UUID> {

    List<ArchLight> findByVersionIdOrderByCreatedAtAsc(UUID versionId);

    List<ArchLight> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    Optional<ArchLight> findByIdAndProjectId(UUID id, UUID projectId);

    long countByVersionId(UUID versionId);
}
