package com.lifepedia.api.note;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Nota em markdown. Arquivada e na lixeira são datas; {@code version} sobe a cada gravação do conteúdo. */
@Entity
@Table(name = "notes")
public class Note extends BaseEntity {

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "group_id", updatable = false)
    private UUID groupId;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "folder_id")
    private UUID folderId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private String content;

    @Column(length = 16)
    private String icon;

    @Column(length = 7)
    private String color;

    @Column(nullable = false, updatable = false)
    private boolean template;

    @Column(name = "pinned_at")
    private OffsetDateTime pinnedAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "commitment_id")
    private UUID commitmentId;

    @Column(nullable = false)
    private long version;

    @ElementCollection
    @CollectionTable(name = "note_tag_links", joinColumns = @JoinColumn(name = "note_id"))
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();

    protected Note() {
    }

    public Note(NoteOwner owner, UUID createdBy, boolean template) {
        this.userId = owner.userId();
        this.groupId = owner.groupId();
        this.createdBy = createdBy;
        this.editedBy = createdBy;
        this.template = template;
    }

    /** Grava o que a pessoa editou; a versão sobe para quem estiver com a antiga receber 409. */
    public void edit(UUID by, String title, String content, UUID folderId, Set<UUID> tagIds, String icon, String color,
                     UUID taskId, UUID commitmentId) {
        this.editedBy = by;
        this.title = title;
        this.content = content;
        this.folderId = folderId;
        this.tagIds = new HashSet<>(tagIds);
        this.icon = icon;
        this.color = color;
        this.taskId = taskId;
        this.commitmentId = commitmentId;
        this.version++;
    }

    public void pin(boolean pinned) {
        this.pinnedAt = pinned ? OffsetDateTime.now() : null;
    }

    public void archive(boolean archived) {
        this.archivedAt = archived ? OffsetDateTime.now() : null;
    }

    public void trash(boolean trashed) {
        this.deletedAt = trashed ? OffsetDateTime.now() : null;
    }

    public boolean ownedBy(NoteOwner owner) {
        return owner.owns(userId, groupId);
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getEditedBy() {
        return editedBy;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }

    public boolean isTemplate() {
        return template;
    }

    public OffsetDateTime getPinnedAt() {
        return pinnedAt;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getCommitmentId() {
        return commitmentId;
    }

    public long getVersion() {
        return version;
    }

    public Set<UUID> getTagIds() {
        return tagIds;
    }
}
