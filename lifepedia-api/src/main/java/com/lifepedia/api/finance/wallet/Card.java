package com.lifepedia.api.finance.wallet;

import com.lifepedia.api.finance.bank.BankKind;
import com.lifepedia.api.finance.bank.BankRef;
import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Cartão de crédito: um por banco, ligado aos lançamentos com esse banco como cartão. */
@Entity
@Table(name = "finance_cards")
public class Card extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "bank_code", nullable = false, length = 30)
    private String bankCode;

    @Column(name = "bank_name", length = 60)
    private String bankName;

    @Column(length = 40)
    private String nickname;

    @Column(name = "last_digits", length = 4)
    private String lastDigits;

    @Column(length = 7)
    private String color;

    @Column(name = "closing_day", nullable = false)
    private int closingDay;

    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(name = "credit_limit", precision = 14, scale = 2)
    private BigDecimal creditLimit;

    @Column(precision = 14, scale = 2)
    private BigDecimal budget;

    @Column(nullable = false)
    private boolean reminders = true;

    @Column(name = "account_id")
    private UUID accountId;

    @ElementCollection
    @CollectionTable(name = "finance_card_groups", joinColumns = @JoinColumn(name = "card_id"))
    @Column(name = "group_id")
    private Set<UUID> groupIds = new HashSet<>();

    protected Card() {
    }

    public Card(UUID userId) {
        this.userId = userId;
    }

    public CardCycle cycle() {
        return new CardCycle(closingDay, dueDay);
    }

    public BankRef bank() {
        return new BankRef(bankCode, bankName, BankKind.CARD);
    }

    public void setBank(BankRef bank) {
        this.bankCode = bank.code();
        this.bankName = bank.name();
    }

    /** O lançamento é deste cartão (mesmo banco, como cartão). */
    public boolean matches(BankRef ref) {
        return ref != null && ref.kind() == BankKind.CARD && ref.key().equals(bank().key());
    }

    /** Apelido ou nome do banco. */
    public String label() {
        return nickname != null ? nickname : bank().label();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBankCode() {
        return bankCode;
    }

    public String getBankName() {
        return bankName;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getLastDigits() {
        return lastDigits;
    }

    public void setLastDigits(String lastDigits) {
        this.lastDigits = lastDigits;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getClosingDay() {
        return closingDay;
    }

    public void setClosingDay(int closingDay) {
        this.closingDay = closingDay;
    }

    public int getDueDay() {
        return dueDay;
    }

    public void setDueDay(int dueDay) {
        this.dueDay = dueDay;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public boolean isReminders() {
        return reminders;
    }

    public void setReminders(boolean reminders) {
        this.reminders = reminders;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public Set<UUID> getGroupIds() {
        return groupIds;
    }
}
