package com.lifepedia.api.admin;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Aviso no topo do app para todos, até {@code endsAt} ou até um admin encerrar. */
@Entity
@Table(name = "announcements")
public class Announcement extends BaseEntity {

    public enum Level { INFO, WARNING }

    @Column(nullable = false, length = 500, updatable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Level level;

    @Column(name = "ends_at", updatable = false)
    private OffsetDateTime endsAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected Announcement() {
    }

    Announcement(String message, Level level, OffsetDateTime endsAt, UUID createdBy) {
        this.message = message;
        this.level = level;
        this.endsAt = endsAt;
        this.createdBy = createdBy;
    }

    public boolean isActive(OffsetDateTime now) {
        return endedAt == null && (endsAt == null || endsAt.isAfter(now));
    }

    void end() {
        if (endedAt == null) endedAt = OffsetDateTime.now();
    }

    public String getMessage() {
        return message;
    }

    public Level getLevel() {
        return level;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
