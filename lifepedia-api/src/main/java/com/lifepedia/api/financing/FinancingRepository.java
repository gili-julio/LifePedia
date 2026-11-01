package com.lifepedia.api.financing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinancingRepository extends JpaRepository<Financing, UUID> {

    List<Financing> findByUserIdOrderByCreatedAtAsc(UUID userId);

    List<Financing> findByGroupIdOrderByCreatedAtAsc(UUID groupId);

    long countByUserId(UUID userId);

    long countByGroupId(UUID groupId);

    List<Financing> findByReminderDaysIsNotNull();
}
