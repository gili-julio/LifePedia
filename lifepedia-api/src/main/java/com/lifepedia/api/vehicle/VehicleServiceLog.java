package com.lifepedia.api.vehicle;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** Registro do que foi feito (item ou revisão), com o gasto quando lançado. */
@Entity
@Table(name = "vehicle_service_logs")
public class VehicleServiceLog extends BaseEntity {

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "item_id", updatable = false)
    private UUID itemId;

    @Column(name = "revision_id", updatable = false)
    private UUID revisionId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "expense_id", updatable = false)
    private UUID expenseId;

    @Column(nullable = false, length = 60, updatable = false)
    private String name;

    @Column(name = "done_on", nullable = false, updatable = false)
    private LocalDate doneOn;

    @Column(updatable = false)
    private Integer km;

    @Column(length = 500, updatable = false)
    private String notes;

    protected VehicleServiceLog() {
    }

    public VehicleServiceLog(UUID vehicleId, UUID itemId, UUID revisionId, UUID userId, UUID expenseId, String name,
                             LocalDate doneOn, Integer km, String notes) {
        this.vehicleId = vehicleId;
        this.itemId = itemId;
        this.revisionId = revisionId;
        this.userId = userId;
        this.expenseId = expenseId;
        this.name = name;
        this.doneOn = doneOn;
        this.km = km;
        this.notes = notes;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public UUID getRevisionId() {
        return revisionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getExpenseId() {
        return expenseId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDoneOn() {
        return doneOn;
    }

    public Integer getKm() {
        return km;
    }

    public String getNotes() {
        return notes;
    }
}
