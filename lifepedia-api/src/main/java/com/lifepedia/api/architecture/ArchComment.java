package com.lifepedia.api.architecture;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Comentário preso a um ponto ({@code x, y, z}, em metros) da maquete de uma versão. */
@Entity
@Table(name = "arch_comments")
public class ArchComment extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false, updatable = false)
    private double x;

    @Column(nullable = false, updatable = false)
    private double y;

    @Column(nullable = false, updatable = false)
    private double z;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected ArchComment() {
    }

    public ArchComment(UUID projectId, UUID versionId, UUID userId, String body, double x, double y, double z) {
        this.projectId = projectId;
        this.versionId = versionId;
        this.userId = userId;
        this.body = body;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBody() {
        return body;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
