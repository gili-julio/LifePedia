package com.lifepedia.api.note;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteActivityRepository extends JpaRepository<NoteActivity, UUID> {

    List<NoteActivity> findTop30ByNoteIdOrderByUpdatedAtDesc(UUID noteId);

    Optional<NoteActivity> findFirstByNoteIdOrderByUpdatedAtDesc(UUID noteId);

    @Modifying(flushAutomatically = true)
    @Query("update NoteActivity a set a.updatedAt = :at where a.id = :id")
    void touch(UUID id, OffsetDateTime at);
}
