package com.lifepedia.api.architecture;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ArchAttachmentRepository extends JpaRepository<ArchAttachment, UUID> {

    List<ArchAttachment> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    long countByProjectId(UUID projectId);

    @Query("select a from ArchAttachment a where a.projectId in (select p.id from ArchProject p where p.userId = :userId)")
    List<ArchAttachment> ofUserProjects(UUID userId);
}
