package com.lifepedia.api.architecture;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Versão da maquete: criada {@code PENDING} e pronta ({@code READY}, com número e arquivo) quando o GLB chega. */
@Entity
@Table(name = "arch_versions")
public class ArchVersion extends BaseEntity {

    public enum Status { PENDING, READY }

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    private Integer number;

    @Column(length = 120)
    private String label;

    @Column(length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(name = "file_key", length = 32)
    private String fileKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "source_name")
    private String sourceName;

    @Column(name = "source_bytes")
    private Long sourceBytes;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected ArchVersion() {
    }

    public ArchVersion(UUID projectId, UUID createdBy, long sizeBytes, String sourceName, Long sourceBytes) {
        this.projectId = projectId;
        this.createdBy = createdBy;
        this.sizeBytes = sizeBytes;
        this.sourceName = sourceName;
        this.sourceBytes = sourceBytes;
    }

    public void ready(int number, String fileKey, long sizeBytes) {
        this.number = number;
        this.fileKey = fileKey;
        this.sizeBytes = sizeBytes;
        this.status = Status.READY;
    }

    public boolean isReady() {
        return status == Status.READY;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public Integer getNumber() {
        return number;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Status getStatus() {
        return status;
    }

    public String getFileKey() {
        return fileKey;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSourceName() {
        return sourceName;
    }

    public Long getSourceBytes() {
        return sourceBytes;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
