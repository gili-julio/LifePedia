package com.lifepedia.api.architecture;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Vista salva: posição da câmera e ponto para onde ela olha, em metros. */
@Entity
@Table(name = "arch_views")
public class ArchView extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String name;

    private double px;
    private double py;
    private double pz;
    private double tx;
    private double ty;
    private double tz;

    protected ArchView() {
    }

    public ArchView(UUID projectId, UUID userId, String name, double[] position, double[] target) {
        this.projectId = projectId;
        this.userId = userId;
        this.name = name;
        this.px = position[0];
        this.py = position[1];
        this.pz = position[2];
        this.tx = target[0];
        this.ty = target[1];
        this.tz = target[2];
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public double[] position() {
        return new double[] {px, py, pz};
    }

    public double[] target() {
        return new double[] {tx, ty, tz};
    }
}
