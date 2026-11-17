package com.lifepedia.api.vehicle;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** Manutenção periódica (a cada X km e/ou Y meses); {@code warned} = último aviso dado. */
@Entity
@Table(name = "vehicle_items")
public class VehicleItem extends BaseEntity {

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "interval_km")
    private Integer intervalKm;

    @Column(name = "interval_months")
    private Integer intervalMonths;

    @Column(name = "last_km")
    private Integer lastKm;

    @Column(name = "last_on")
    private LocalDate lastOn;

    @Column(nullable = false)
    private boolean notify = true;

    @Enumerated(EnumType.STRING)
    @Column(length = 6)
    private VehicleMath.Stage warned;

    @Column(nullable = false)
    private int position;

    protected VehicleItem() {
    }

    public VehicleItem(UUID vehicleId, int position) {
        this.vehicleId = vehicleId;
        this.position = position;
    }

    public void set(String name, Integer intervalKm, Integer intervalMonths, boolean notify) {
        this.name = name;
        this.intervalKm = intervalKm;
        this.intervalMonths = intervalMonths;
        this.notify = notify;
        this.warned = null;
    }

    /** Última vez feita; zera o aviso. */
    public void setLast(Integer lastKm, LocalDate lastOn) {
        this.lastKm = lastKm;
        this.lastOn = lastOn;
        this.warned = null;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getName() {
        return name;
    }

    public Integer getIntervalKm() {
        return intervalKm;
    }

    public Integer getIntervalMonths() {
        return intervalMonths;
    }

    public Integer getLastKm() {
        return lastKm;
    }

    public LocalDate getLastOn() {
        return lastOn;
    }

    public boolean isNotify() {
        return notify;
    }

    public VehicleMath.Stage getWarned() {
        return warned;
    }

    public void setWarned(VehicleMath.Stage warned) {
        this.warned = warned;
    }

    public int getPosition() {
        return position;
    }
}
