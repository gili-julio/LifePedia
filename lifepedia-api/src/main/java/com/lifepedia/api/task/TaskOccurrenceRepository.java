package com.lifepedia.api.task;

import com.lifepedia.api.task.TaskOccurrence.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskOccurrenceRepository extends JpaRepository<TaskOccurrence, UUID> {

    /** Registros no intervalo (pelo dia da regra ou adiados para ele) e o do dia atual de cada tarefa. */
    @Query("""
            select o from TaskOccurrence o where o.taskId in :taskIds
              and (o.date between :lower and :upper or o.movedTo between :lower and :upper
                   or o.date = (select t.dueDate from Task t where t.id = o.taskId))
            """)
    List<TaskOccurrence> window(Collection<UUID> taskIds, LocalDate lower, LocalDate upper);

    @Query("select o from TaskOccurrence o where o.taskId in :taskIds and o.date between :from and :to")
    List<TaskOccurrence> between(Collection<UUID> taskIds, LocalDate from, LocalDate to);

    List<TaskOccurrence> findByTaskIdIn(Collection<UUID> taskIds);

    @Query("select o from TaskOccurrence o where o.taskId in :taskIds and o.date is null")
    List<TaskOccurrence> undated(Collection<UUID> taskIds);

    @Query("select o from TaskOccurrence o where o.taskId = :taskId and o.date is not distinct from :date")
    Optional<TaskOccurrence> find(UUID taskId, LocalDate date);

    @Query("select o from TaskOccurrence o where o.taskId = :taskId and o.date >= :from")
    List<TaskOccurrence> from(UUID taskId, LocalDate from);

    @Query("select o from TaskOccurrence o where o.taskId = :taskId and o.status in :statuses order by o.date desc")
    List<TaskOccurrence> resolved(UUID taskId, Collection<Status> statuses);

    @Query(value = """
            select o from TaskOccurrence o join Task t on t.id = o.taskId
            where (t.userId = :userId or t.groupId = :groupId) and o.status = :status
            """, countQuery = """
            select count(o) from TaskOccurrence o join Task t on t.id = o.taskId
            where (t.userId = :userId or t.groupId = :groupId) and o.status = :status
            """)
    Page<TaskOccurrence> history(UUID userId, UUID groupId, Status status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TaskOccurrence o set o.assigneeId = null where o.assigneeId = :userId
              and o.taskId in (select t.id from Task t where t.groupId = :groupId)
            """)
    int clearAssignee(UUID groupId, UUID userId);
}
