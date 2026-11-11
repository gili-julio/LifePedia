package com.lifepedia.api.architecture;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArchVariantRepository extends JpaRepository<ArchVariant, UUID> {

    List<ArchVariant> findByVersionIdOrderByCreatedAtAsc(UUID versionId);

    List<ArchVariant> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    Optional<ArchVariant> findByIdAndProjectId(UUID id, UUID projectId);

    long countByVersionId(UUID versionId);
}
