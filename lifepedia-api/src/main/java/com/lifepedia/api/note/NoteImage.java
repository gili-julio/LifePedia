package com.lifepedia.api.note;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Foto enviada para dentro de uma nota (arquivo no {@link NoteImageStore}). */
@Entity
@Table(name = "note_images")
public class NoteImage extends BaseEntity {

    @Column(name = "note_id", nullable = false, updatable = false)
    private UUID noteId;

    @Column(name = "image_key", nullable = false, updatable = false, length = 32)
    private String imageKey;

    protected NoteImage() {
    }

    public NoteImage(UUID noteId, String imageKey) {
        this.noteId = noteId;
        this.imageKey = imageKey;
    }

    public UUID getNoteId() {
        return noteId;
    }

    public String getImageKey() {
        return imageKey;
    }
}
