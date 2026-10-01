package com.lifepedia.api.planning;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Conjunto nomeado de itens considerados na simulação. */
@Entity
@Table(name = "plan_scenarios")
public class PlanScenario extends BaseEntity {

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    /** Quem criou (nulo se a conta foi excluída). */
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String name;

    @ElementCollection
    @CollectionTable(name = "plan_scenario_items", joinColumns = @JoinColumn(name = "scenario_id"))
    @Column(name = "item_id", nullable = false)
    private Set<UUID> itemIds = new HashSet<>();

    protected PlanScenario() {
    }

    public PlanScenario(UUID groupId, UUID userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    public void set(String name, Collection<UUID> itemIds) {
        this.name = name;
        this.itemIds.clear();
        this.itemIds.addAll(itemIds);
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public Set<UUID> getItemIds() {
        return itemIds;
    }
}
