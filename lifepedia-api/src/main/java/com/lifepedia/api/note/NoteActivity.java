package com.lifepedia.api.note;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** O que alguém fez numa nota; edições seguidas da mesma pessoa reaproveitam a linha ({@code updatedAt} = última). */
@Entity
@Table(name = "note_activities")
public class NoteActivity extends BaseEntity {

    public enum Action { CREATED, EDITED, ARCHIVED, UNARCHIVED, TRASHED, RESTORED }

    @Column(name = "note_id", nullable = false, updatable = false)
    private UUID noteId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12, updatable = false)
    private Action action;

    protected NoteActivity() {
    }

    public NoteActivity(UUID noteId, UUID userId, Action action) {
        this.noteId = noteId;
        this.userId = userId;
        this.action = action;
    }

    public UUID getUserId() {
        return userId;
    }

    public Action getAction() {
        return action;
    }
}
