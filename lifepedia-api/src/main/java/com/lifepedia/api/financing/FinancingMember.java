package com.lifepedia.api.financing;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Quem paga o financiamento e a parte (%); {@code recurringEntryId} = fixo pessoal com a parte dela. */
@Entity
@Table(name = "financing_members")
public class FinancingMember extends BaseEntity {

    @Column(name = "financing_id", nullable = false, updatable = false)
    private UUID financingId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal share;

    @Column(name = "recurring_entry_id")
    private UUID recurringEntryId;

    protected FinancingMember() {
    }

    public FinancingMember(UUID financingId, UUID userId, BigDecimal share) {
        this.financingId = financingId;
        this.userId = userId;
        this.share = share;
    }

    public UUID getFinancingId() {
        return financingId;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getShare() {
        return share;
    }

    public void setShare(BigDecimal share) {
        this.share = share;
    }

    public UUID getRecurringEntryId() {
        return recurringEntryId;
    }

    public void setRecurringEntryId(UUID recurringEntryId) {
        this.recurringEntryId = recurringEntryId;
    }
}
