package com.lifepedia.api.dev;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** O que a pessoa fez num dia num projeto do Dev. */
@Entity
@Table(name = "dev_reports")
public class DevReport extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private LocalDate day;

    @Column(nullable = false, length = 4000)
    private String text;

    protected DevReport() {
    }

    public DevReport(UUID projectId, UUID userId, LocalDate day) {
        this.projectId = projectId;
        this.userId = userId;
        this.day = day;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public LocalDate getDay() {
        return day;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
