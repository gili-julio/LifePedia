package com.lifepedia.api.finance.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinanceSimulationRepository extends JpaRepository<FinanceSimulation, UUID> {

    List<FinanceSimulation> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<FinanceSimulation> findByUserIdAndIdIn(UUID userId, List<UUID> ids);

    Optional<FinanceSimulation> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
