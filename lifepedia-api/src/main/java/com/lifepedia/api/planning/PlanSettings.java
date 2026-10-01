package com.lifepedia.api.planning;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Base da simulação do grupo: reserva atual, meses da média dos ocasionais e saldo manual (opcional). */
@Entity
@Table(name = "plan_settings")
public class PlanSettings extends BaseEntity {

    public static final int DEFAULT_AVERAGE_MONTHS = 3;

    @Column(name = "group_id", nullable = false, updatable = false, unique = true)
    private UUID groupId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal reserve = BigDecimal.ZERO;

    @Column(name = "average_months", nullable = false)
    private int averageMonths = DEFAULT_AVERAGE_MONTHS;

    @Column(name = "manual_balance", precision = 14, scale = 2)
    private BigDecimal manualBalance;

    protected PlanSettings() {
    }

    public PlanSettings(UUID groupId) {
        this.groupId = groupId;
    }

    public void set(BigDecimal reserve, int averageMonths, BigDecimal manualBalance) {
        this.reserve = reserve;
        this.averageMonths = averageMonths;
        this.manualBalance = manualBalance;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public BigDecimal getReserve() {
        return reserve;
    }

    public int getAverageMonths() {
        return averageMonths;
    }

    public BigDecimal getManualBalance() {
        return manualBalance;
    }
}
