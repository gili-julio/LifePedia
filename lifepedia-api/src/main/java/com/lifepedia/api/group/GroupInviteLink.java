package com.lifepedia.api.group;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Link de convite: quem tiver o token (e estiver logado) entra no grupo até expirar. */
@Entity
@Table(name = "group_invite_links")
public class GroupInviteLink extends BaseEntity {

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected GroupInviteLink() {
    }

    public GroupInviteLink(UUID groupId) {
        this.groupId = groupId;
    }

    /** Novo token (invalida o anterior). */
    public void renew(String token, OffsetDateTime expiresAt, UUID createdBy) {
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(OffsetDateTime.now());
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getToken() {
        return token;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
