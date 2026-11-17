package com.lifepedia.api.finance.wallet;

import com.lifepedia.api.finance.bank.BankKind;
import com.lifepedia.api.finance.bank.BankRef;
import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Conta corrente: saldo informado numa data, projetado pelos lançamentos com esse banco como conta. */
@Entity
@Table(name = "finance_accounts")
public class BankAccount extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "bank_code", nullable = false, length = 30)
    private String bankCode;

    @Column(name = "bank_name", length = 60)
    private String bankName;

    @Column(length = 40)
    private String nickname;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    @Column(name = "balance_on", nullable = false)
    private LocalDate balanceOn;

    protected BankAccount() {
    }

    public BankAccount(UUID userId) {
        this.userId = userId;
    }

    public BankRef bank() {
        return new BankRef(bankCode, bankName, BankKind.ACCOUNT);
    }

    public void setBank(BankRef bank) {
        this.bankCode = bank.code();
        this.bankName = bank.name();
    }

    /** O lançamento é desta conta (mesmo banco, como conta). */
    public boolean matches(BankRef ref) {
        return ref != null && ref.kind() == BankKind.ACCOUNT && ref.key().equals(bank().key());
    }

    /** Mesmo banco, sem olhar conta ou cartão. */
    public boolean sameBank(BankRef ref) {
        return ref != null && ref.key().equals(bank().key());
    }

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

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public LocalDate getBalanceOn() {
        return balanceOn;
    }

    public void setBalanceOn(LocalDate balanceOn) {
        this.balanceOn = balanceOn;
    }
}
