package com.lifepedia.api.vehicle;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Gasto do veículo; {@code userId} = quem lançou e {@code transactionId} = lançamento no financeiro dessa pessoa. */
@Entity
@Table(name = "vehicle_expenses")
public class VehicleExpense extends BaseEntity {

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ExpenseKind kind;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 120)
    private String description;

    private Integer odometer;

    @Column(precision = 7, scale = 2)
    private BigDecimal liters;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private VehicleFuel fuel;

    @Column(name = "full_tank", nullable = false)
    private boolean fullTank;

    @Column(length = 60)
    private String station;

    @Column(length = 2000)
    private String notes;

    @Column(name = "image_key", length = 80)
    private String imageKey;

    @Column(name = "transaction_id")
    private UUID transactionId;

    protected VehicleExpense() {
    }

    public VehicleExpense(UUID vehicleId, UUID userId) {
        this.vehicleId = vehicleId;
        this.userId = userId;
    }

    public void set(ExpenseKind kind, LocalDate spentOn, BigDecimal amount, String description, Integer odometer, String notes) {
        this.kind = kind;
        this.spentOn = spentOn;
        this.amount = amount;
        this.description = description;
        this.odometer = odometer;
        this.notes = notes;
    }

    /** Só no abastecimento; nos outros tipos fica vazio. */
    public void setFuelData(BigDecimal liters, VehicleFuel fuel, boolean fullTank, String station) {
        this.liters = liters;
        this.fuel = fuel;
        this.fullTank = fullTank;
        this.station = station;
    }

    public String imageUrl() {
        if (imageKey == null) return null;
        return "/api/vehicles/" + vehicleId + "/expenses/" + getId() + "/image?v=" + imageKey.substring(0, Math.min(8, imageKey.length()));
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ExpenseKind getKind() {
        return kind;
    }

    public LocalDate getSpentOn() {
        return spentOn;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Integer getOdometer() {
        return odometer;
    }

    public BigDecimal getLiters() {
        return liters;
    }

    public VehicleFuel getFuel() {
        return fuel;
    }

    public boolean isFullTank() {
        return fullTank;
    }

    public String getStation() {
        return station;
    }

    public String getNotes() {
        return notes;
    }

    public String getImageKey() {
        return imageKey;
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }
}
