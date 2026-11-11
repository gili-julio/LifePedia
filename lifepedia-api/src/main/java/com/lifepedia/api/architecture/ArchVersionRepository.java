package com.lifepedia.api.architecture;

import com.lifepedia.api.architecture.ArchVersion.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArchVersionRepository extends JpaRepository<ArchVersion, UUID> {

    List<ArchVersion> findByProjectIdAndStatusOrderByNumberDesc(UUID projectId, Status status);

    Optional<ArchVersion> findFirstByProjectIdAndStatusOrderByNumberDesc(UUID projectId, Status status);

    List<ArchVersion> findByIdIn(Collection<UUID> ids);

    List<ArchVersion> findByStatusAndCreatedAtBefore(Status status, OffsetDateTime before);

    long countByProjectIdAndStatus(UUID projectId, Status status);

    @Query("select coalesce(max(v.number), 0) from ArchVersion v where v.projectId = :projectId")
    int maxNumber(UUID projectId);

    @Query("select v.fileKey from ArchVersion v where v.projectId = :projectId and v.fileKey is not null")
    List<String> keysOfProject(UUID projectId);

    @Query("select v.fileKey from ArchVersion v where v.fileKey is not null "
            + "and v.projectId in (select p.id from ArchProject p where p.userId = :userId)")
    List<String> keysOfUser(UUID userId);
}
