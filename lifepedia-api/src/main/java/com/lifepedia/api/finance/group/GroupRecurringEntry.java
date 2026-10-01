package com.lifepedia.api.finance.group;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.YearMonthConverter;
import com.lifepedia.api.finance.recurring.RecurringEntry;
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

/** Fixo levado para um grupo. Sincronizado usa também os ajustes mensais do fixo pessoal; cópia usa só o valor. */
@Entity
@Table(name = "group_recurring_entries")
public class GroupRecurringEntry extends BaseEntity {

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "source_id", updatable = false)
    private UUID sourceId;

    @Column(name = "origin_id", updatable = false)
    private UUID originId;

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

    @Column(length = 500)
    private String notes;

    protected GroupRecurringEntry() {
    }

    /** Fixo cadastrado direto no grupo (sem item pessoal). */
    public GroupRecurringEntry(UUID groupId, UUID userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    public GroupRecurringEntry(UUID groupId, RecurringEntry source, ShareMode mode) {
        this.groupId = groupId;
        this.userId = source.getUserId();
        this.sourceId = mode == ShareMode.SYNC ? source.getId() : null;
        this.originId = source.getId();
        copyFrom(source);
    }

    public void copyFrom(RecurringEntry e) {
        set(e.getDescription(), e.getType(), e.getCategoryId(), e.getAmount(), e.getDayOfMonth(), e.getStartMonth(),
                e.getEndMonth(), e.isActive(), e.getNotes());
    }

    public void set(String description, EntryType type, UUID categoryId, BigDecimal amount, Integer dayOfMonth,
                    YearMonth startMonth, YearMonth endMonth, boolean active, String notes) {
        this.description = description;
        this.type = type;
        this.categoryId = categoryId;
        this.amount = amount;
        this.dayOfMonth = dayOfMonth;
        this.startMonth = startMonth;
        this.endMonth = endMonth;
        this.active = active;
        this.notes = notes;
    }

    public boolean appliesTo(YearMonth month) {
        return active && !month.isBefore(startMonth) && (endMonth == null || !month.isAfter(endMonth));
    }

    public boolean isSynced() {
        return sourceId != null;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public UUID getOriginId() {
        return originId;
    }

    public String getDescription() {
        return description;
    }

    public EntryType getType() {
        return type;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public YearMonth getStartMonth() {
        return startMonth;
    }

    public YearMonth getEndMonth() {
        return endMonth;
    }

    public boolean isActive() {
        return active;
    }

    public String getNotes() {
        return notes;
    }
}
