package com.lifepedia.api.finance.group;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.YearMonthConverter;
import com.lifepedia.api.finance.transaction.Transaction;
import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** Ocasional levado para um grupo: sincronizado com o pessoal ({@code sourceId}) ou cópia independente. */
@Entity
@Table(name = "group_transactions")
public class GroupTransaction extends BaseEntity {

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    /** Quem levou (dono da categoria). */
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

    @Column(nullable = false)
    private LocalDate date;

    @Convert(converter = YearMonthConverter.class)
    @Column(nullable = false)
    private YearMonth month;

    @Column(length = 500)
    private String notes;

    protected GroupTransaction() {
    }

    /** Cópia sem item pessoal de origem (ex.: compra do planejamento). */
    public GroupTransaction(UUID groupId, UUID userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    public GroupTransaction(UUID groupId, Transaction source, ShareMode mode) {
        this.groupId = groupId;
        this.userId = source.getUserId();
        this.sourceId = mode == ShareMode.SYNC ? source.getId() : null;
        this.originId = source.getId();
        copyFrom(source);
    }

    public void copyFrom(Transaction t) {
        set(t.getDescription(), t.getType(), t.getCategoryId(), t.getAmount(), t.getDate(), t.getMonth(), t.getNotes());
    }

    public void set(String description, EntryType type, UUID categoryId, BigDecimal amount, LocalDate date,
                    YearMonth month, String notes) {
        this.description = description;
        this.type = type;
        this.categoryId = categoryId;
        this.amount = amount;
        this.date = date;
        this.month = month;
        this.notes = notes;
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

    public LocalDate getDate() {
        return date;
    }

    public YearMonth getMonth() {
        return month;
    }

    public String getNotes() {
        return notes;
    }
}
