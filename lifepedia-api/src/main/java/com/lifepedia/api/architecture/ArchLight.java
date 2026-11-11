package com.lifepedia.api.architecture;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Luz colocada na maquete para a vista da noite. */
@Entity
@Table(name = "arch_lights")
public class ArchLight extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    private double x;
    private double y;
    private double z;

    @Column(nullable = false, length = 7)
    private String color;

    private double intensity;

    protected ArchLight() {
    }

    public ArchLight(UUID projectId, UUID versionId, UUID userId, double[] point, String color, double intensity) {
        this.projectId = projectId;
        this.versionId = versionId;
        this.userId = userId;
        this.x = point[0];
        this.y = point[1];
        this.z = point[2];
        this.color = color;
        this.intensity = intensity;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public double[] point() {
        return new double[] {x, y, z};
    }

    public String getColor() {
        return color;
    }

    public double getIntensity() {
        return intensity;
    }
}
