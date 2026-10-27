package com.lifepedia.api.group;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "group_members")
public class GroupMember extends BaseEntity {

    public enum Role { ADMIN, MEMBER }

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    /** Fixado na sidebar de quem é membro (nulo = não fixado). */
    @Column(name = "pinned_at")
    private OffsetDateTime pinnedAt;

    protected GroupMember() {
    }

    public GroupMember(UUID groupId, UUID userId, Role role) {
        this.groupId = groupId;
        this.userId = userId;
        this.role = role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public OffsetDateTime getPinnedAt() {
        return pinnedAt;
    }

    public void setPinnedAt(OffsetDateTime pinnedAt) {
        this.pinnedAt = pinnedAt;
    }
}
