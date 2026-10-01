package com.lifepedia.api.group;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Convite direto para um usuário; ele aceita ou recusa. */
@Entity
@Table(name = "group_invites")
public class GroupInvite extends BaseEntity {

    public enum Status { PENDING, ACCEPTED, DECLINED, CANCELED }

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "invited_by", nullable = false, updatable = false)
    private UUID invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    protected GroupInvite() {
    }

    public GroupInvite(UUID groupId, UUID userId, UUID invitedBy) {
        this.groupId = groupId;
        this.userId = userId;
        this.invitedBy = invitedBy;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
