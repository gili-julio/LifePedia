package com.lifepedia.api.financing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancingExtraPaymentRepository extends JpaRepository<FinancingExtraPayment, UUID> {

    List<FinancingExtraPayment> findByFinancingIdOrderByMonthAsc(UUID financingId);

    List<FinancingExtraPayment> findByFinancingIdIn(Collection<UUID> financingIds);

    Optional<FinancingExtraPayment> findByIdAndFinancingId(UUID id, UUID financingId);

    long countByFinancingId(UUID financingId);
}
