package com.lifepedia.api.finance.simulation;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Simulação salva do financeiro; {@code input} é o pedido em JSON. */
@Entity
@Table(name = "finance_simulations")
public class FinanceSimulation extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20000)
    private String input;

    protected FinanceSimulation() {
    }

    public FinanceSimulation(UUID userId, String name, String input) {
        this.userId = userId;
        set(name, input);
    }

    public void set(String name, String input) {
        this.name = name;
        this.input = input;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getInput() {
        return input;
    }
}
