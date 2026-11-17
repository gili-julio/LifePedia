package com.lifepedia.api.vehicle;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Veículo da pessoa; {@code odometer} = maior leitura registrada (data em {@code odometerOn}). */
@Entity
@Table(name = "vehicles")
public class Vehicle extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private VehicleType type;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 60)
    private String brand;

    @Column(length = 120)
    private String model;

    @Column(name = "model_year")
    private Integer modelYear;

    @Column(length = 10)
    private String plate;

    @Column(length = 30)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private VehicleFuel fuel;

    @Column(name = "tank_liters", precision = 6, scale = 1)
    private BigDecimal tankLiters;

    @Column(nullable = false)
    private int odometer;

    @Column(name = "odometer_on")
    private LocalDate odometerOn;

    @Column(name = "image_key", length = 80)
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "fipe_kind", length = 12)
    private FipeKind fipeKind;

    @Column(name = "fipe_brand", length = 10)
    private String fipeBrand;

    @Column(name = "fipe_model", length = 10)
    private String fipeModel;

    @Column(name = "fipe_year", length = 12)
    private String fipeYear;

    @Column(name = "fipe_code", length = 12)
    private String fipeCode;

    @Column(name = "fipe_value", precision = 12, scale = 2)
    private BigDecimal fipeValue;

    @Column(name = "fipe_month", length = 30)
    private String fipeMonth;

    @Column(name = "fipe_checked_at")
    private OffsetDateTime fipeCheckedAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    protected Vehicle() {
    }

    public Vehicle(UUID userId) {
        this.userId = userId;
    }

    /** Dados do cadastro (o odômetro muda só pelas leituras). */
    public void set(VehicleType type, String name, String brand, String model, Integer modelYear, String plate, String color,
                    VehicleFuel fuel, BigDecimal tankLiters, String notes) {
        this.type = type;
        this.name = name;
        this.brand = brand;
        this.model = model;
        this.modelYear = modelYear;
        this.plate = plate;
        this.color = color;
        this.fuel = fuel;
        this.tankLiters = tankLiters;
        this.notes = notes;
    }

    /** Referência na FIPE; mudar a referência apaga o valor lido antes. */
    public void setFipeRef(FipeKind kind, String brand, String model, String year) {
        boolean same = kind == fipeKind && eq(brand, fipeBrand) && eq(model, fipeModel) && eq(year, fipeYear);
        this.fipeKind = kind;
        this.fipeBrand = brand;
        this.fipeModel = model;
        this.fipeYear = year;
        if (!same) setFipeValue(null, null, null, null);
    }

    public void setFipeValue(String code, BigDecimal value, String month, OffsetDateTime checkedAt) {
        this.fipeCode = code;
        this.fipeValue = value;
        this.fipeMonth = month;
        this.fipeCheckedAt = checkedAt;
    }

    public boolean hasFipe() {
        return fipeKind != null && fipeBrand != null && fipeModel != null && fipeYear != null;
    }

    public String imageUrl() {
        return imageKey == null ? null : "/api/vehicles/" + getId() + "/image?v=" + imageKey.substring(0, Math.min(8, imageKey.length()));
    }

    public UUID getUserId() {
        return userId;
    }

    public VehicleType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public Integer getModelYear() {
        return modelYear;
    }

    public String getPlate() {
        return plate;
    }

    public String getColor() {
        return color;
    }

    public VehicleFuel getFuel() {
        return fuel;
    }

    public BigDecimal getTankLiters() {
        return tankLiters;
    }

    public int getOdometer() {
        return odometer;
    }

    public LocalDate getOdometerOn() {
        return odometerOn;
    }

    public void setOdometer(int odometer, LocalDate odometerOn) {
        this.odometer = odometer;
        this.odometerOn = odometerOn;
    }

    public String getImageKey() {
        return imageKey;
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public FipeKind getFipeKind() {
        return fipeKind;
    }

    public String getFipeBrand() {
        return fipeBrand;
    }

    public String getFipeModel() {
        return fipeModel;
    }

    public String getFipeYear() {
        return fipeYear;
    }

    public String getFipeCode() {
        return fipeCode;
    }

    public BigDecimal getFipeValue() {
        return fipeValue;
    }

    public String getFipeMonth() {
        return fipeMonth;
    }

    public OffsetDateTime getFipeCheckedAt() {
        return fipeCheckedAt;
    }

    public String getNotes() {
        return notes;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(OffsetDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
