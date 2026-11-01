package com.lifepedia.api.financing;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Gasto de ter o bem (IPVA, seguro, revisão, IPTU, condomínio), mensal ou anual. */
@Entity
@Table(name = "financing_costs")
public class FinancingCost extends BaseEntity {

    public enum Frequency { MONTHLY, YEARLY }

    @Column(name = "financing_id", nullable = false, updatable = false)
    private UUID financingId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Frequency frequency;

    @Column(name = "due_month")
    private Integer dueMonth;

    @Column(name = "due_day", nullable = false)
    private int dueDay;

    protected FinancingCost() {
    }

    public FinancingCost(UUID financingId) {
        this.financingId = financingId;
    }

    /** Próximo vencimento a partir de {@code today} (inclusive). */
    public LocalDate nextDate(LocalDate today) {
        if (frequency == Frequency.MONTHLY) {
            LocalDate date = clamp(today.getYear(), today.getMonthValue());
            return date.isBefore(today) ? clamp(today.plusMonths(1).getYear(), today.plusMonths(1).getMonthValue()) : date;
        }
        LocalDate date = clamp(today.getYear(), dueMonth);
        return date.isBefore(today) ? clamp(today.getYear() + 1, dueMonth) : date;
    }

    public void set(String name, BigDecimal amount, Frequency frequency, Integer dueMonth, int dueDay) {
        this.name = name;
        this.amount = amount;
        this.frequency = frequency;
        this.dueMonth = frequency == Frequency.YEARLY ? dueMonth : null;
        this.dueDay = dueDay;
    }

    public UUID getFinancingId() {
        return financingId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public Integer getDueMonth() {
        return dueMonth;
    }

    public int getDueDay() {
        return dueDay;
    }

    private LocalDate clamp(int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        return first.withDayOfMonth(Math.min(dueDay, first.lengthOfMonth()));
    }
}
