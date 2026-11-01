package com.lifepedia.api.financing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinancingSimulationRepository extends JpaRepository<FinancingSimulation, UUID> {

    List<FinancingSimulation> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<FinancingSimulation> findByGroupIdOrderByCreatedAtDesc(UUID groupId);

    long countByUserId(UUID userId);

    long countByGroupId(UUID groupId);
}
