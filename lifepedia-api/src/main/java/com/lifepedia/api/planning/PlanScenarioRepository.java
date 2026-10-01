package com.lifepedia.api.planning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanScenarioRepository extends JpaRepository<PlanScenario, UUID> {

    List<PlanScenario> findByGroupIdOrderByCreatedAtAsc(UUID groupId);

    Optional<PlanScenario> findByIdAndGroupId(UUID id, UUID groupId);

    long countByGroupId(UUID groupId);
}
