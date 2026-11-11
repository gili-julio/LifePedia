package com.lifepedia.api.architecture;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Projeto compartilhado com uma pessoa ({@code userId}) ou um grupo ({@code groupId}); o outro é nulo. */
@Entity
@Table(name = "arch_shares")
public class ArchShare extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "group_id", updatable = false)
    private UUID groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ArchRole role;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected ArchShare() {
    }

    private ArchShare(UUID projectId, ArchRole role, UUID createdBy) {
        this.projectId = projectId;
        this.role = role;
        this.createdBy = createdBy;
    }

    public static ArchShare withUser(UUID projectId, UUID userId, ArchRole role, UUID createdBy) {
        ArchShare share = new ArchShare(projectId, role, createdBy);
        share.userId = userId;
        return share;
    }

    public static ArchShare withGroup(UUID projectId, UUID groupId, ArchRole role, UUID createdBy) {
        ArchShare share = new ArchShare(projectId, role, createdBy);
        share.groupId = groupId;
        return share;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public ArchRole getRole() {
        return role;
    }

    public void setRole(ArchRole role) {
        this.role = role;
    }
}
