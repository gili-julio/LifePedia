package com.lifepedia.api.architecture;

import com.lifepedia.api.architecture.ArchitectureFiles.Type;
import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Render ou foto (JPEG regravado) ou planta em PDF de um projeto. */
@Entity
@Table(name = "arch_attachments")
public class ArchAttachment extends BaseEntity {

    public enum Kind { IMAGE, PDF }

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Kind kind;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "file_key", nullable = false, updatable = false, length = 32)
    private String fileKey;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    protected ArchAttachment() {
    }

    public ArchAttachment(UUID projectId, UUID userId, Kind kind, String name, String fileKey, long sizeBytes) {
        this.projectId = projectId;
        this.userId = userId;
        this.kind = kind;
        this.name = name;
        this.fileKey = fileKey;
        this.sizeBytes = sizeBytes;
    }

    public Type fileType() {
        return kind == Kind.PDF ? Type.PDF : Type.IMAGE;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getFileKey() {
        return fileKey;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }
}
