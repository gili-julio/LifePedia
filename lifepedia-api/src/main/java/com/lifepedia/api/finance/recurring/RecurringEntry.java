package com.lifepedia.api.finance.recurring;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.YearMonthConverter;
import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/** Lançamento fixo mensal, vigente de {@code startMonth} a {@code endMonth} (nulo = sem fim). */
@Entity
@Table(name = "recurring_entries")
public class RecurringEntry extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EntryType type;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Convert(converter = YearMonthConverter.class)
    @Column(name = "start_month", nullable = false)
    private YearMonth startMonth;

    @Convert(converter = YearMonthConverter.class)
    @Column(name = "end_month")
    private YearMonth endMonth;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 10000)
    private String notes;

    protected RecurringEntry() {
    }

    public RecurringEntry(UUID userId) {
        this.userId = userId;
    }

    public boolean appliesTo(YearMonth month) {
        return active
                && !month.isBefore(startMonth)
                && (endMonth == null || !month.isAfter(endMonth));
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public EntryType getType() {
        return type;
    }

    public void setType(EntryType type) {
        this.type = type;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public YearMonth getStartMonth() {
        return startMonth;
    }

    public void setStartMonth(YearMonth startMonth) {
        this.startMonth = startMonth;
    }

    public YearMonth getEndMonth() {
        return endMonth;
    }

    public void setEndMonth(YearMonth endMonth) {
        this.endMonth = endMonth;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
