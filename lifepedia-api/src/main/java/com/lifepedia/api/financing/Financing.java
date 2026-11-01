package com.lifepedia.api.financing;

import com.lifepedia.api.finance.YearMonthConverter;
import com.lifepedia.api.financing.FinancingMath.AmortizationSystem;
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

/** Financiamento em andamento: dados do contrato; as parcelas são calculadas na leitura. */
@Entity
@Table(name = "financings")
public class Financing extends BaseEntity {

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "group_id", updatable = false)
    private UUID groupId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FinancingKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false, length = 10)
    private ItemCondition condition;

    @Column(precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "down_payment", precision = 14, scale = 2)
    private BigDecimal downPayment;

    @Column(precision = 14, scale = 2)
    private BigDecimal fgts;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal financed;

    @Column(name = "monthly_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal monthlyRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private AmortizationSystem system;

    @Column(nullable = false)
    private int months;

    @Convert(converter = YearMonthConverter.class)
    @Column(name = "first_month", nullable = false)
    private YearMonth firstMonth;

    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(name = "insurance_rate", nullable = false, precision = 8, scale = 5)
    private BigDecimal insuranceRate = BigDecimal.ZERO;

    @Column(name = "monthly_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyFee = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "correction_index", nullable = false, length = 10)
    private CorrectionIndex index = CorrectionIndex.NONE;

    @Column(length = 160)
    private String bank;

    @Column(name = "reminder_days")
    private Integer reminderDays;

    @Column(length = 2000)
    private String notes;

    protected Financing() {
    }

    public Financing(FinancingOwner owner, UUID createdBy) {
        this.userId = owner.userId();
        this.groupId = owner.groupId();
        this.createdBy = createdBy;
    }

    /** Vencimento da parcela do mês (dia ajustado ao fim de meses curtos). */
    public LocalDate dueDate(YearMonth month) {
        return month.atDay(Math.min(dueDay, month.lengthOfMonth()));
    }

    public FinancingOwner owner() {
        return new FinancingOwner(userId, groupId);
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public FinancingKind getKind() {
        return kind;
    }

    public void setKind(FinancingKind kind) {
        this.kind = kind;
    }

    public ItemCondition getCondition() {
        return condition;
    }

    public void setCondition(ItemCondition condition) {
        this.condition = condition;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getDownPayment() {
        return downPayment;
    }

    public void setDownPayment(BigDecimal downPayment) {
        this.downPayment = downPayment;
    }

    public BigDecimal getFgts() {
        return fgts;
    }

    public void setFgts(BigDecimal fgts) {
        this.fgts = fgts;
    }

    public BigDecimal getFinanced() {
        return financed;
    }

    public void setFinanced(BigDecimal financed) {
        this.financed = financed;
    }

    public BigDecimal getMonthlyRate() {
        return monthlyRate;
    }

    public void setMonthlyRate(BigDecimal monthlyRate) {
        this.monthlyRate = monthlyRate;
    }

    public AmortizationSystem getSystem() {
        return system;
    }

    public void setSystem(AmortizationSystem system) {
        this.system = system;
    }

    public int getMonths() {
        return months;
    }

    public void setMonths(int months) {
        this.months = months;
    }

    public YearMonth getFirstMonth() {
        return firstMonth;
    }

    public void setFirstMonth(YearMonth firstMonth) {
        this.firstMonth = firstMonth;
    }

    public int getDueDay() {
        return dueDay;
    }

    public void setDueDay(int dueDay) {
        this.dueDay = dueDay;
    }

    public BigDecimal getInsuranceRate() {
        return insuranceRate;
    }

    public void setInsuranceRate(BigDecimal insuranceRate) {
        this.insuranceRate = insuranceRate;
    }

    public BigDecimal getMonthlyFee() {
        return monthlyFee;
    }

    public void setMonthlyFee(BigDecimal monthlyFee) {
        this.monthlyFee = monthlyFee;
    }

    public CorrectionIndex getIndex() {
        return index;
    }

    public void setIndex(CorrectionIndex index) {
        this.index = index;
    }

    public String getBank() {
        return bank;
    }

    public void setBank(String bank) {
        this.bank = bank;
    }

    public Integer getReminderDays() {
        return reminderDays;
    }

    public void setReminderDays(Integer reminderDays) {
        this.reminderDays = reminderDays;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
