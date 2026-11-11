package com.lifepedia.api.architecture;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Variante de acabamento de uma versão: ajustes de cor/textura por material, em JSON. */
@Entity
@Table(name = "arch_variants")
public class ArchVariant extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private String materials;

    protected ArchVariant() {
    }

    public ArchVariant(UUID projectId, UUID versionId, UUID userId, String name, String materials) {
        this.projectId = projectId;
        this.versionId = versionId;
        this.userId = userId;
        this.name = name;
        this.materials = materials;
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

    public String getMaterials() {
        return materials;
    }

    public void setMaterials(String materials) {
        this.materials = materials;
    }
}
