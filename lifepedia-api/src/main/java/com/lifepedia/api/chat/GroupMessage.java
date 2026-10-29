package com.lifepedia.api.chat;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Mensagem do chat do grupo; apagada fica sem texto ({@code deletedAt}) para manter o lugar na conversa. */
@Entity
@Table(name = "group_messages")
public class GroupMessage extends BaseEntity {

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "reply_to_id", updatable = false)
    private UUID replyToId;

    @Column(length = 2000)
    private String body;

    @Column(name = "edited_at")
    private OffsetDateTime editedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected GroupMessage() {
    }

    public GroupMessage(UUID groupId, UUID userId, UUID replyToId, String body) {
        this.groupId = groupId;
        this.userId = userId;
        this.replyToId = replyToId;
        this.body = body;
    }

    public void edit(String body) {
        this.body = body;
        this.editedAt = OffsetDateTime.now();
    }

    public void delete() {
        this.body = null;
        this.deletedAt = OffsetDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getReplyToId() {
        return replyToId;
    }

    public String getBody() {
        return body;
    }

    public OffsetDateTime getEditedAt() {
        return editedAt;
    }
}
