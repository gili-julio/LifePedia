package com.lifepedia.api.finance.recurring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RecurringAdjustmentRepository extends JpaRepository<RecurringAdjustment, UUID> {

    List<RecurringAdjustment> findByUserId(UUID userId);

    List<RecurringAdjustment> findByRecurringEntryId(UUID recurringEntryId);

    List<RecurringAdjustment> findByRecurringEntryIdIn(Collection<UUID> recurringEntryIds);
}
