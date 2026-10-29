package com.lifepedia.api.note;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface NoteTagRepository extends JpaRepository<NoteTag, UUID> {

    @Query("select t from NoteTag t where t.userId = :userId or t.groupId = :groupId order by lower(t.name)")
    List<NoteTag> owned(UUID userId, UUID groupId);

    @Query("""
            select count(t) > 0 from NoteTag t where (t.userId = :userId or t.groupId = :groupId)
              and lower(t.name) = lower(:name) and (:exceptId is null or t.id <> :exceptId)
            """)
    boolean nameTaken(UUID userId, UUID groupId, String name, UUID exceptId);
}
