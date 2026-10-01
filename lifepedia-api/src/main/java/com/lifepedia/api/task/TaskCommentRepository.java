package com.lifepedia.api.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

    @Query(value = "select c from TaskComment c where c.taskId = :taskId",
            countQuery = "select count(c) from TaskComment c where c.taskId = :taskId")
    Page<TaskComment> page(UUID taskId, Pageable pageable);

    Optional<TaskComment> findByIdAndTaskId(UUID id, UUID taskId);

    long countByTaskId(UUID taskId);

    @Query("select c.taskId, count(c) from TaskComment c where c.taskId in :taskIds group by c.taskId")
    List<Object[]> countByTasks(Collection<UUID> taskIds);

    @Query("select distinct c.userId from TaskComment c where c.taskId = :taskId and c.userId is not null")
    List<UUID> authors(UUID taskId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from TaskComment c where c.userId = :userId
              and c.taskId in (select t.id from Task t where t.groupId = :groupId)
            """)
    int deleteByGroupAndUser(UUID groupId, UUID userId);
}
