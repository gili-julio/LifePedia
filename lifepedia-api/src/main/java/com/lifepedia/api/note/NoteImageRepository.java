package com.lifepedia.api.note;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteImageRepository extends JpaRepository<NoteImage, UUID> {

    List<NoteImage> findByNoteId(UUID noteId);

    List<NoteImage> findByNoteIdIn(Collection<UUID> noteIds);

    long countByNoteId(UUID noteId);

    Optional<NoteImage> findByNoteIdAndImageKey(UUID noteId, String imageKey);

    @Query("select i.imageKey from NoteImage i where i.noteId in (select n.id from Note n where n.groupId = :groupId)")
    List<String> keysOfGroup(UUID groupId);

    @Query("select i.imageKey from NoteImage i where i.noteId in (select n.id from Note n where n.userId = :userId)")
    List<String> keysOfUser(UUID userId);
}
