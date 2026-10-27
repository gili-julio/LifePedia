package com.lifepedia.api.group;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Grupo de usuários (família, casal, república...). */
@Entity
@Table(name = "user_groups")
public class UserGroup extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String name;

    /** Arquivo da imagem no storage; nulo = sem imagem. */
    @Column(name = "image_key", length = 64)
    private String imageKey;

    /** Nulo quando quem criou excluiu a conta. */
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    /** Última escrita no grupo (tarefa, financeiro, planejamento, membros); atualizada por {@link GroupActivity}. */
    @Column(name = "last_activity_at", nullable = false)
    private OffsetDateTime lastActivityAt;

    protected UserGroup() {
    }

    public UserGroup(String name, UUID createdBy) {
        this.name = name;
        this.createdBy = createdBy;
        this.lastActivityAt = OffsetDateTime.now();
    }

    /** URL da imagem com versão (cache); nulo sem imagem. */
    public String imageUrl() {
        return imageKey == null ? null : "/api/groups/" + getId() + "/image?v=" + imageKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImageKey() {
        return imageKey;
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getLastActivityAt() {
        return lastActivityAt;
    }
}
