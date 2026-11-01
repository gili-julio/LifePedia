package com.lifepedia.api.financing;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Taxa média de um banco na última divulgação do Banco Central (% ao mês e ao ano). */
@Entity
@Table(name = "financing_rates")
public class FinancingRate extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Modality modality;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 160)
    private String bank;

    @Column(name = "monthly_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal monthlyRate;

    @Column(name = "yearly_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal yearlyRate;

    protected FinancingRate() {
    }

    public FinancingRate(Modality modality, LocalDate periodStart, int position, String bank, BigDecimal monthlyRate,
                         BigDecimal yearlyRate) {
        this.modality = modality;
        this.periodStart = periodStart;
        this.position = position;
        this.bank = bank;
        this.monthlyRate = monthlyRate;
        this.yearlyRate = yearlyRate;
    }

    public Modality getModality() {
        return modality;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public int getPosition() {
        return position;
    }

    public String getBank() {
        return bank;
    }

    public BigDecimal getMonthlyRate() {
        return monthlyRate;
    }

    public BigDecimal getYearlyRate() {
        return yearlyRate;
    }
}
