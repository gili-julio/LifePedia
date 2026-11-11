package com.lifepedia.api.architecture;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Link público da maquete: quem tiver o token vê a versão atual sem login, até {@code expiresAt}. */
@Entity
@Table(name = "arch_links")
public class ArchLink extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false, length = 64)
    private String token;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected ArchLink() {
    }

    public ArchLink(UUID projectId, String token, OffsetDateTime expiresAt, UUID createdBy) {
        this.projectId = projectId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getToken() {
        return token;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
