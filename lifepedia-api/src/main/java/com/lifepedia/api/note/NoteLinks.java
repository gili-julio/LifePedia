package com.lifepedia.api.note;

import com.lifepedia.api.note.NoteDtos.FolderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** O que outros módulos (ex.: Escola) fazem com as pastas de notas pessoais. */
@Service
public class NoteLinks {

    private static final int MAX_NAME = 80;

    private final NoteFolderRepository folders;
    private final NoteLabelService labels;

    public NoteLinks(NoteFolderRepository folders, NoteLabelService labels) {
        this.folders = folders;
        this.labels = labels;
    }

    /** Pasta pessoal com esse nome (a que já existe ou uma nova). */
    @Transactional
    public UUID personalFolder(UUID userId, String name) {
        String clean = name.trim().length() <= MAX_NAME ? name.trim() : name.trim().substring(0, MAX_NAME);
        return folders.owned(userId, null).stream().filter(f -> f.getName().equalsIgnoreCase(clean)).map(NoteFolder::getId).findFirst()
                .orElseGet(() -> labels.createFolder(NoteOwner.personal(userId), new FolderRequest(clean)).id());
    }

    /** Pasta pessoal da pessoa (vazio se foi apagada ou é de outra). */
    @Transactional(readOnly = true)
    public Optional<UUID> personalFolderId(UUID userId, UUID folderId) {
        return folders.owned(userId, null).stream().map(NoteFolder::getId).filter(folderId::equals).findFirst();
    }
}
