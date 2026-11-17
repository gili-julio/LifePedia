package com.lifepedia.api.vehicle;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Veículo compartilhado com uma pessoa ({@code userId}) ou um grupo ({@code groupId}); o outro é nulo. */
@Entity
@Table(name = "vehicle_shares")
public class VehicleShare extends BaseEntity {

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "group_id", updatable = false)
    private UUID groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VehicleRole role;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected VehicleShare() {
    }

    private VehicleShare(UUID vehicleId, VehicleRole role, UUID createdBy) {
        this.vehicleId = vehicleId;
        this.role = role;
        this.createdBy = createdBy;
    }

    public static VehicleShare withUser(UUID vehicleId, UUID userId, VehicleRole role, UUID createdBy) {
        VehicleShare share = new VehicleShare(vehicleId, role, createdBy);
        share.userId = userId;
        return share;
    }

    public static VehicleShare withGroup(UUID vehicleId, UUID groupId, VehicleRole role, UUID createdBy) {
        VehicleShare share = new VehicleShare(vehicleId, role, createdBy);
        share.groupId = groupId;
        return share;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public VehicleRole getRole() {
        return role;
    }

    public void setRole(VehicleRole role) {
        this.role = role;
    }
}
