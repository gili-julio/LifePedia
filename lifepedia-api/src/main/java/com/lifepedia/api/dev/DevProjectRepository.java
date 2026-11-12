package com.lifepedia.api.dev;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DevProjectRepository extends JpaRepository<DevProject, UUID> {

    List<DevProject> findByUserIdOrderByPositionAscCreatedAtAsc(UUID userId);

    Optional<DevProject> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Query("select coalesce(max(p.position), 0) from DevProject p where p.userId = :userId")
    int maxPosition(UUID userId);
}
