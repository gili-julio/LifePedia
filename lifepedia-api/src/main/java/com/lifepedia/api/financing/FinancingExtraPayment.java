package com.lifepedia.api.financing;

import com.lifepedia.api.finance.YearMonthConverter;
import com.lifepedia.api.financing.FinancingMath.ExtraMode;
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

/** Amortização extra feita (ou planejada) num mês. */
@Entity
@Table(name = "financing_extra_payments")
public class FinancingExtraPayment extends BaseEntity {

    @Column(name = "financing_id", nullable = false, updatable = false)
    private UUID financingId;

    @Convert(converter = YearMonthConverter.class)
    @Column(nullable = false)
    private YearMonth month;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExtraMode mode;

    protected FinancingExtraPayment() {
    }

    public FinancingExtraPayment(UUID financingId, YearMonth month, BigDecimal amount, ExtraMode mode) {
        this.financingId = financingId;
        this.month = month;
        this.amount = amount;
        this.mode = mode;
    }

    public UUID getFinancingId() {
        return financingId;
    }

    public YearMonth getMonth() {
        return month;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public ExtraMode getMode() {
        return mode;
    }
}
