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

/** Documento com vencimento (IPVA, licenciamento, seguro, CNH...). */
@Entity
@Table(name = "vehicle_documents")
public class VehicleDocument extends BaseEntity {

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private DocumentKind kind;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(length = 6)
    private VehicleMath.Stage warned;

    protected VehicleDocument() {
    }

    public VehicleDocument(UUID vehicleId) {
        this.vehicleId = vehicleId;
    }

    public void set(DocumentKind kind, String name, LocalDate dueOn, BigDecimal amount, String notes) {
        this.kind = kind;
        this.name = name;
        this.dueOn = dueOn;
        this.amount = amount;
        this.notes = notes;
        this.warned = null;
    }

    /** Pago/renovado: próximo vencimento e aviso zerado. */
    public void renew(LocalDate nextDue) {
        this.dueOn = nextDue;
        this.warned = null;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public DocumentKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getNotes() {
        return notes;
    }

    public VehicleMath.Stage getWarned() {
        return warned;
    }

    public void setWarned(VehicleMath.Stage warned) {
        this.warned = warned;
    }
}
