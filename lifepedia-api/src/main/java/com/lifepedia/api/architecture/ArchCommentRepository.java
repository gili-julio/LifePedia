package com.lifepedia.api.architecture;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArchCommentRepository extends JpaRepository<ArchComment, UUID> {

    List<ArchComment> findByVersionIdOrderByCreatedAtAsc(UUID versionId);

    List<ArchComment> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<ArchComment> findByIdAndProjectId(UUID id, UUID projectId);

    long countByVersionId(UUID versionId);

    long countByProjectIdAndResolvedAtIsNull(UUID projectId);
}
