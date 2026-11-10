package com.lifepedia.api.admin;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Ação de um admin (ou do servidor, sem {@code actorId}); nomes gravados na época. */
@Entity
@Table(name = "admin_audit_log")
public class AuditEntry extends BaseEntity {

    public enum Action {
        ADMIN_GRANTED, ADMIN_REVOKED, ADMIN_FIXED, USER_BLOCKED, USER_UNBLOCKED, SESSIONS_REVOKED,
        ANNOUNCEMENT_CREATED, ANNOUNCEMENT_ENDED, BROADCAST_SENT
    }

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_name", nullable = false, length = 120, updatable = false)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private Action action;

    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    @Column(name = "target_name", length = 120, updatable = false)
    private String targetName;

    @Column(length = 300, updatable = false)
    private String detail;

    protected AuditEntry() {
    }

    AuditEntry(UUID actorId, String actorName, Action action, UUID targetId, String targetName, String detail) {
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.targetId = targetId;
        this.targetName = targetName;
        this.detail = detail == null || detail.length() <= 300 ? detail : detail.substring(0, 299) + "…";
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public Action getAction() {
        return action;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getDetail() {
        return detail;
    }
}
