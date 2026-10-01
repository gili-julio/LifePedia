package com.lifepedia.api.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TaskListRepository extends JpaRepository<TaskList, UUID> {

    @Query("select l from TaskList l where l.userId = :userId or l.groupId = :groupId order by lower(l.name)")
    List<TaskList> owned(UUID userId, UUID groupId);

    @Query("""
            select count(l) > 0 from TaskList l where (l.userId = :userId or l.groupId = :groupId)
              and lower(l.name) = lower(:name) and (:exceptId is null or l.id <> :exceptId)
            """)
    boolean nameTaken(UUID userId, UUID groupId, String name, UUID exceptId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TaskList l where l.userId = :userId")
    int deleteAllOfUser(UUID userId);
}
