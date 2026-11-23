package com.lifepedia.api.school;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolCourseRepository extends JpaRepository<SchoolCourse, UUID> {

    List<SchoolCourse> findByEnrollmentId(UUID enrollmentId);

    List<SchoolCourse> findByUserId(UUID userId);

    Optional<SchoolCourse> findByIdAndUserId(UUID id, UUID userId);

    long countByEnrollmentId(UUID enrollmentId);

    List<SchoolCourse> findByGroupIdInAndUserIdNot(Collection<UUID> groupIds, UUID userId);

    List<SchoolCourse> findByUserIdAndGroupId(UUID userId, UUID groupId);

    List<SchoolCourse> findByStatusIn(Collection<SchoolCourse.Status> statuses);
}
