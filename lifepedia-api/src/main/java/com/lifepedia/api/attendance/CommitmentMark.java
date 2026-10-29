package com.lifepedia.api.attendance;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** Presença, falta ou "sem aula" marcada num dia do compromisso. */
@Entity
@Table(name = "commitment_marks")
public class CommitmentMark extends BaseEntity {

    public enum Mark { PRESENT, ABSENT, CANCELED }

    @Column(name = "commitment_id", nullable = false, updatable = false)
    private UUID commitmentId;

    @Column(name = "mark_date", nullable = false, updatable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Mark status;

    protected CommitmentMark() {
    }

    public CommitmentMark(UUID commitmentId, LocalDate date) {
        this.commitmentId = commitmentId;
        this.date = date;
    }

    public UUID getCommitmentId() {
        return commitmentId;
    }

    public LocalDate getDate() {
        return date;
    }

    public Mark getStatus() {
        return status;
    }

    public void setStatus(Mark status) {
        this.status = status;
    }
}
