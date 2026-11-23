package com.lifepedia.api.school;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolEnrollmentRepository extends JpaRepository<SchoolEnrollment, UUID> {

    List<SchoolEnrollment> findByUserIdOrderByPositionAscCreatedAtAsc(UUID userId);

    Optional<SchoolEnrollment> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Query("select coalesce(max(e.position), 0) from SchoolEnrollment e where e.userId = :userId")
    int maxPosition(UUID userId);
}
