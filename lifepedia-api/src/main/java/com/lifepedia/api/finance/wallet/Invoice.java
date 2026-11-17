package com.lifepedia.api.finance.wallet;

import com.lifepedia.api.finance.YearMonthConverter;
import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** O que é guardado de uma fatura: pagamento e avisos já enviados. Os valores vêm sempre dos lançamentos. */
@Entity
@Table(name = "finance_invoices")
public class Invoice extends BaseEntity {

    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID cardId;

    @Convert(converter = YearMonthConverter.class)
    @Column(nullable = false, updatable = false)
    private YearMonth month;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "paid_amount", precision = 14, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "paid_account")
    private UUID paidAccount;

    @Column(name = "warned_closing", nullable = false)
    private boolean warnedClosing;

    @Column(name = "warned_due", nullable = false)
    private boolean warnedDue;

    /** Maior faixa da meta já avisada (0, 80 ou 100). */
    @Column(name = "warned_budget", nullable = false)
    private int warnedBudget;

    protected Invoice() {
    }

    public Invoice(UUID cardId, YearMonth month) {
        this.cardId = cardId;
        this.month = month;
    }

    public boolean isPaid() {
        return paidOn != null;
    }

    public void pay(LocalDate on, BigDecimal amount, UUID accountId) {
        this.paidOn = on;
        this.paidAmount = amount;
        this.paidAccount = accountId;
    }

    public void unpay() {
        this.paidOn = null;
        this.paidAmount = null;
        this.paidAccount = null;
    }

    public UUID getCardId() {
        return cardId;
    }

    public YearMonth getMonth() {
        return month;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public UUID getPaidAccount() {
        return paidAccount;
    }

    public boolean isWarnedClosing() {
        return warnedClosing;
    }

    public void setWarnedClosing(boolean warnedClosing) {
        this.warnedClosing = warnedClosing;
    }

    public boolean isWarnedDue() {
        return warnedDue;
    }

    public void setWarnedDue(boolean warnedDue) {
        this.warnedDue = warnedDue;
    }

    public int getWarnedBudget() {
        return warnedBudget;
    }

    public void setWarnedBudget(int warnedBudget) {
        this.warnedBudget = warnedBudget;
    }
}
