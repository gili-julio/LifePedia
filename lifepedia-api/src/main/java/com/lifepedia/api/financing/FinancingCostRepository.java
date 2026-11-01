package com.lifepedia.api.financing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancingCostRepository extends JpaRepository<FinancingCost, UUID> {

    List<FinancingCost> findByFinancingIdOrderByCreatedAtAsc(UUID financingId);

    List<FinancingCost> findByFinancingIdIn(Collection<UUID> financingIds);

    Optional<FinancingCost> findByIdAndFinancingId(UUID id, UUID financingId);

    long countByFinancingId(UUID financingId);
}
