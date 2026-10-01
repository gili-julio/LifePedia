package com.lifepedia.api.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TaskTagRepository extends JpaRepository<TaskTag, UUID> {

    @Query("select g from TaskTag g where g.userId = :userId or g.groupId = :groupId order by lower(g.name)")
    List<TaskTag> owned(UUID userId, UUID groupId);

    @Query("""
            select count(g) > 0 from TaskTag g where (g.userId = :userId or g.groupId = :groupId)
              and lower(g.name) = lower(:name) and (:exceptId is null or g.id <> :exceptId)
            """)
    boolean nameTaken(UUID userId, UUID groupId, String name, UUID exceptId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TaskTag g where g.userId = :userId")
    int deleteAllOfUser(UUID userId);
}
