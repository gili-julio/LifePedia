package com.lifepedia.api.notification;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Notificação para um usuário; o texto é montado no front a partir de {@code type} e {@code data}. */
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private NotificationType type;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "group_id", updatable = false)
    private UUID groupId;

    /** JSON com os parâmetros do texto (nomes no momento do envio). */
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String data;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    protected Notification() {
    }

    public Notification(UUID userId, NotificationType type, UUID actorId, UUID groupId, String data) {
        this.userId = userId;
        this.type = type;
        this.actorId = actorId;
        this.groupId = groupId;
        this.data = data;
    }

    public void markRead() {
        if (readAt == null) readAt = OffsetDateTime.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public UUID getActorId() {
        return actorId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getData() {
        return data;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }
}
