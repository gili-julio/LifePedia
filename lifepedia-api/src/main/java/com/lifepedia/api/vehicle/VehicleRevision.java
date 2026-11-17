package com.lifepedia.api.vehicle;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** Revisão do plano (vale o que vier primeiro: km ou data); {@code doneOn} preenchido = feita. */
@Entity
@Table(name = "vehicle_revisions")
public class VehicleRevision extends BaseEntity {

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "due_km")
    private Integer dueKm;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Column(name = "done_on")
    private LocalDate doneOn;

    @Column(name = "done_km")
    private Integer doneKm;

    @Column(length = 60)
    private String shop;

    @Column(length = 2000)
    private String notes;

    @Column(name = "expense_id")
    private UUID expenseId;

    @Enumerated(EnumType.STRING)
    @Column(length = 6)
    private VehicleMath.Stage warned;

    protected VehicleRevision() {
    }

    public VehicleRevision(UUID vehicleId) {
        this.vehicleId = vehicleId;
    }

    public void set(String name, Integer dueKm, LocalDate dueOn, String notes) {
        this.name = name;
        this.dueKm = dueKm;
        this.dueOn = dueOn;
        this.notes = notes;
        this.warned = null;
    }

    public void done(LocalDate doneOn, Integer doneKm, String shop, UUID expenseId) {
        this.doneOn = doneOn;
        this.doneKm = doneKm;
        this.shop = shop;
        this.expenseId = expenseId;
    }

    public void reopen() {
        done(null, null, null, null);
        this.warned = null;
    }

    public boolean isDone() {
        return doneOn != null;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getName() {
        return name;
    }

    public Integer getDueKm() {
        return dueKm;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public LocalDate getDoneOn() {
        return doneOn;
    }

    public Integer getDoneKm() {
        return doneKm;
    }

    public String getShop() {
        return shop;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getExpenseId() {
        return expenseId;
    }

    public VehicleMath.Stage getWarned() {
        return warned;
    }

    public void setWarned(VehicleMath.Stage warned) {
        this.warned = warned;
    }
}
