package com.lifepedia.api.architecture;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArchRoomRepository extends JpaRepository<ArchRoom, UUID> {

    List<ArchRoom> findByVersionIdOrderByCreatedAtAsc(UUID versionId);

    List<ArchRoom> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    Optional<ArchRoom> findByIdAndProjectId(UUID id, UUID projectId);

    long countByVersionId(UUID versionId);
}
