package com.lifepedia.api.vehicle;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** Leitura do odômetro; {@code expenseId} = veio de um gasto (some e muda junto com ele). */
@Entity
@Table(name = "vehicle_readings")
public class VehicleReading extends BaseEntity {

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "expense_id", updatable = false)
    private UUID expenseId;

    @Column(nullable = false)
    private int km;

    @Column(name = "read_on", nullable = false)
    private LocalDate readOn;

    protected VehicleReading() {
    }

    public VehicleReading(UUID vehicleId, UUID userId, UUID expenseId, int km, LocalDate readOn) {
        this.vehicleId = vehicleId;
        this.userId = userId;
        this.expenseId = expenseId;
        this.km = km;
        this.readOn = readOn;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getExpenseId() {
        return expenseId;
    }

    public int getKm() {
        return km;
    }

    public LocalDate getReadOn() {
        return readOn;
    }

    public void set(int km, LocalDate readOn) {
        this.km = km;
        this.readOn = readOn;
    }
}
