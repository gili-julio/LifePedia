package com.lifepedia.api.note;

import java.util.UUID;

/**
 * Dono das notas e quem age.
 *
 * @param admin pode excluir qualquer nota (pessoal: sempre; grupo: administrador)
 */
public record NoteScope(NoteOwner owner, UUID userId, boolean admin) {

    public static NoteScope personal(UUID userId) {
        return new NoteScope(NoteOwner.personal(userId), userId, true);
    }

    /** Criador ou administrador. */
    public boolean canDelete(Note note) {
        return admin || userId.equals(note.getCreatedBy());
    }
}
