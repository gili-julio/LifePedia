package com.lifepedia.api.note;

import com.lifepedia.api.note.NoteDtos.Folder;
import com.lifepedia.api.note.NoteDtos.FolderRequest;
import com.lifepedia.api.note.NoteDtos.Labels;
import com.lifepedia.api.note.NoteDtos.Tag;
import com.lifepedia.api.note.NoteDtos.TagRequest;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pastas e etiquetas de um dono (usuário ou grupo). Excluir não apaga notas: só as desliga. */
@Service
public class NoteLabelService {

    static final int MAX_FOLDERS = 100;
    static final int MAX_TAGS = 100;

    private final NoteFolderRepository folders;
    private final NoteTagRepository tags;
    private final NoteRepository notes;

    public NoteLabelService(NoteFolderRepository folders, NoteTagRepository tags, NoteRepository notes) {
        this.folders = folders;
        this.tags = tags;
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public Labels labels(NoteOwner owner) {
        List<NoteFolder> ownFolders = folders.owned(owner.userId(), owner.groupId());
        List<NoteTag> ownTags = tags.owned(owner.userId(), owner.groupId());
        Map<UUID, Long> folderCounts = ownFolders.isEmpty() ? Map.of()
                : counts(notes.countByFolder(ownFolders.stream().map(NoteFolder::getId).toList()));
        Map<UUID, Long> tagCounts = ownTags.isEmpty() ? Map.of()
                : counts(notes.countByTag(ownTags.stream().map(NoteTag::getId).toList()));
        return new Labels(
                ownFolders.stream().map(f -> new Folder(f.getId(), f.getName(), folderCounts.getOrDefault(f.getId(), 0L))).toList(),
                ownTags.stream().map(t -> new Tag(t.getId(), t.getName(), t.getColor(), tagCounts.getOrDefault(t.getId(), 0L))).toList());
    }

    @Transactional
    public Folder createFolder(NoteOwner owner, FolderRequest request) {
        if (folders.owned(owner.userId(), owner.groupId()).size() >= MAX_FOLDERS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_FOLDERS + " pastas.");
        }
        String name = request.name().trim();
        requireFreeFolderName(owner, name, null);
        NoteFolder folder = folders.save(new NoteFolder(owner, name));
        return new Folder(folder.getId(), folder.getName(), 0);
    }

    @Transactional
    public Folder renameFolder(NoteOwner owner, UUID id, FolderRequest request) {
        NoteFolder folder = folder(owner, id);
        String name = request.name().trim();
        requireFreeFolderName(owner, name, id);
        folder.rename(name);
        return new Folder(folder.getId(), folder.getName(), 0);
    }

    @Transactional
    public void deleteFolder(NoteOwner owner, UUID id) {
        folders.delete(folder(owner, id));
    }

    @Transactional
    public Tag createTag(NoteOwner owner, TagRequest request) {
        if (tags.owned(owner.userId(), owner.groupId()).size() >= MAX_TAGS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_TAGS + " etiquetas.");
        }
        String name = request.name().trim();
        requireFreeTagName(owner, name, null);
        NoteTag tag = tags.save(new NoteTag(owner, name, request.color()));
        return new Tag(tag.getId(), tag.getName(), tag.getColor(), 0);
    }

    @Transactional
    public Tag updateTag(NoteOwner owner, UUID id, TagRequest request) {
        NoteTag tag = tag(owner, id);
        String name = request.name().trim();
        requireFreeTagName(owner, name, id);
        tag.set(name, request.color());
        return new Tag(tag.getId(), tag.getName(), tag.getColor(), 0);
    }

    @Transactional
    public void deleteTag(NoteOwner owner, UUID id) {
        tags.delete(tag(owner, id));
    }

    /** Pasta usável numa nota do dono (400 se não for). */
    void requireFolder(NoteOwner owner, UUID folderId) {
        if (folderId != null && folders.findById(folderId).filter(f -> f.ownedBy(owner)).isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Pasta inválida.");
        }
    }

    void requireTags(NoteOwner owner, Collection<UUID> tagIds) {
        if (tagIds.isEmpty()) return;
        List<NoteTag> found = tags.findAllById(tagIds);
        if (found.size() != tagIds.size() || !found.stream().allMatch(t -> t.ownedBy(owner))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Etiqueta inválida.");
        }
    }

    private NoteFolder folder(NoteOwner owner, UUID id) {
        return folders.findById(id).filter(f -> f.ownedBy(owner))
                .orElseThrow(() -> new NotFoundException("Pasta não encontrada."));
    }

    private NoteTag tag(NoteOwner owner, UUID id) {
        return tags.findById(id).filter(t -> t.ownedBy(owner))
                .orElseThrow(() -> new NotFoundException("Etiqueta não encontrada."));
    }

    private void requireFreeFolderName(NoteOwner owner, String name, UUID exceptId) {
        if (folders.nameTaken(owner.userId(), owner.groupId(), name, exceptId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Já existe uma pasta com esse nome.");
        }
    }

    private void requireFreeTagName(NoteOwner owner, String name, UUID exceptId) {
        if (tags.nameTaken(owner.userId(), owner.groupId(), name, exceptId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Já existe uma etiqueta com esse nome.");
        }
    }

    private static Map<UUID, Long> counts(List<Object[]> rows) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) map.put((UUID) row[0], (Long) row[1]);
        return map;
    }
}
