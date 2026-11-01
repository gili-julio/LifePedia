package com.lifepedia.api.financing;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Simulação salva; {@code input} é o pedido em JSON (refeita com as taxas do dia ao abrir). */
@Entity
@Table(name = "financing_simulations")
public class FinancingSimulation extends BaseEntity {

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "group_id", updatable = false)
    private UUID groupId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20000)
    private String input;

    protected FinancingSimulation() {
    }

    public FinancingSimulation(FinancingOwner owner, UUID createdBy, String name, String input) {
        this.userId = owner.userId();
        this.groupId = owner.groupId();
        this.createdBy = createdBy;
        this.name = name;
        this.input = input;
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

    public String getInput() {
        return input;
    }
}
