package com.lifepedia.api.note;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface NoteFolderRepository extends JpaRepository<NoteFolder, UUID> {

    @Query("select f from NoteFolder f where f.userId = :userId or f.groupId = :groupId order by lower(f.name)")
    List<NoteFolder> owned(UUID userId, UUID groupId);

    @Query("""
            select count(f) > 0 from NoteFolder f where (f.userId = :userId or f.groupId = :groupId)
              and lower(f.name) = lower(:name) and (:exceptId is null or f.id <> :exceptId)
            """)
    boolean nameTaken(UUID userId, UUID groupId, String name, UUID exceptId);
}
