package com.lifepedia.api.school;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolAssessmentRepository extends JpaRepository<SchoolAssessment, UUID> {

    List<SchoolAssessment> findByCourseIdIn(Collection<UUID> courseIds);

    List<SchoolAssessment> findByCourseId(UUID courseId);

    Optional<SchoolAssessment> findByIdAndCourseId(UUID id, UUID courseId);

    long countByCourseId(UUID courseId);
}
