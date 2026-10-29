package com.lifepedia.api.note;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Pasta (caderno) de notas. */
@Entity
@Table(name = "note_folders")
public class NoteFolder extends BaseEntity {

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "group_id", updatable = false)
    private UUID groupId;

    @Column(nullable = false, length = 80)
    private String name;

    protected NoteFolder() {
    }

    public NoteFolder(NoteOwner owner, String name) {
        this.userId = owner.userId();
        this.groupId = owner.groupId();
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public boolean ownedBy(NoteOwner owner) {
        return owner.owns(userId, groupId);
    }

    public String getName() {
        return name;
    }
}
