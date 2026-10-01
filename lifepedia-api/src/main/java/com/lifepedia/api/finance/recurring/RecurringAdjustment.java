package com.lifepedia.api.finance.recurring;

import com.lifepedia.api.finance.YearMonthConverter;
import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/** Valor real de um fixo num mês, no lugar do valor cadastrado. */
@Entity
@Table(name = "recurring_adjustments")
public class RecurringAdjustment extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "recurring_entry_id", nullable = false, updatable = false)
    private UUID recurringEntryId;

    @Convert(converter = YearMonthConverter.class)
    @Column(nullable = false, updatable = false)
    private YearMonth month;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    protected RecurringAdjustment() {
    }

    public RecurringAdjustment(UUID userId, UUID recurringEntryId, YearMonth month, BigDecimal amount) {
        this.userId = userId;
        this.recurringEntryId = recurringEntryId;
        this.month = month;
        this.amount = amount;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRecurringEntryId() {
        return recurringEntryId;
    }

    public YearMonth getMonth() {
        return month;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
