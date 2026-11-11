package com.lifepedia.api.architecture;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Projeto de arquitetura (uma casa, um ambiente) do usuário {@code userId}, com versões da maquete. */
@Entity
@Table(name = "arch_projects")
public class ArchProject extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(precision = 8, scale = 5)
    private BigDecimal latitude;

    @Column(name = "north_degrees", nullable = false)
    private int northDegrees;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "cover_key", length = 32)
    private String coverKey;

    protected ArchProject() {
    }

    public ArchProject(UUID userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    /** Muda o {@code updated_at} (versão nova, anexo, comentário). */
    public void touch() {
        onUpdate();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public int getNorthDegrees() {
        return northDegrees;
    }

    public void setNorthDegrees(int northDegrees) {
        this.northDegrees = northDegrees;
    }

    public UUID getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(UUID currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public String getCoverKey() {
        return coverKey;
    }

    public void setCoverKey(String coverKey) {
        this.coverKey = coverKey;
    }
}
