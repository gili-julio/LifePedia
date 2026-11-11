package com.lifepedia.api.architecture;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Cômodo marcado na maquete: ponto no piso, altura do teto, área e contorno (JSON de pares x/z). */
@Entity
@Table(name = "arch_rooms")
public class ArchRoom extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String name;

    private double x;
    private double y;
    private double z;
    private Double ceiling;
    private BigDecimal area;

    @Column(nullable = false)
    private String outline;

    protected ArchRoom() {
    }

    public ArchRoom(UUID projectId, UUID versionId, UUID userId, String name, double[] point, Double ceiling, BigDecimal area,
                    String outline) {
        this.projectId = projectId;
        this.versionId = versionId;
        this.userId = userId;
        this.name = name;
        this.x = point[0];
        this.y = point[1];
        this.z = point[2];
        this.ceiling = ceiling;
        this.area = area;
        this.outline = outline;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double[] point() {
        return new double[] {x, y, z};
    }

    public Double getCeiling() {
        return ceiling;
    }

    public BigDecimal getArea() {
        return area;
    }

    public String getOutline() {
        return outline;
    }
}
