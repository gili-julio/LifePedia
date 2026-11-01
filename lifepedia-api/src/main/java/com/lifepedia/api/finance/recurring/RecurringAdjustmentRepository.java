package com.lifepedia.api.finance.recurring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RecurringAdjustmentRepository extends JpaRepository<RecurringAdjustment, UUID> {

    List<RecurringAdjustment> findByUserId(UUID userId);

    List<RecurringAdjustment> findByRecurringEntryId(UUID recurringEntryId);

    List<RecurringAdjustment> findByRecurringEntryIdIn(Collection<UUID> recurringEntryIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RecurringAdjustment a where a.recurringEntryId = :recurringEntryId")
    int deleteByRecurringEntryId(UUID recurringEntryId);
}
