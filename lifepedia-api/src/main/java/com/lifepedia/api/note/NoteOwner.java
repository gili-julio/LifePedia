package com.lifepedia.api.note;

import java.util.UUID;

/** Dono das notas, pastas e etiquetas: um usuário (pessoal) ou um grupo; o outro campo é nulo. */
public record NoteOwner(UUID userId, UUID groupId) {

    public static NoteOwner personal(UUID userId) {
        return new NoteOwner(userId, null);
    }

    public static NoteOwner group(UUID groupId) {
        return new NoteOwner(null, groupId);
    }

    public boolean owns(UUID userId, UUID groupId) {
        return this.userId != null ? this.userId.equals(userId) : this.groupId.equals(groupId);
    }

    public boolean isGroup() {
        return groupId != null;
    }
}
