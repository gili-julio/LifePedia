package com.lifepedia.api.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Consultas por dono: {@code userId} (pessoal) ou {@code groupId}; o outro vai nulo. */
public interface TaskRepository extends JpaRepository<Task, UUID> {

    /**
     * Tarefas com algo entre {@code lower} e {@code to}: pendentes (inclusive atrasadas) ou com ocorrência
     * registrada/adiada no intervalo.
     */
    @Query("""
            select t from Task t where (t.userId = :userId or t.groupId = :groupId) and t.dueDate is not null
              and ((t.completedAt is null and t.dueDate <= :to and (t.repeatUntil is null or t.repeatUntil >= :lower))
                or exists (select 1 from TaskOccurrence o where o.taskId = t.id
                           and (o.date between :from and :to or o.movedTo between :from and :to)))
            """)
    List<Task> agenda(UUID userId, UUID groupId, LocalDate lower, LocalDate from, LocalDate to);

    @Query("""
            select t from Task t where (t.userId = :userId or t.groupId = :groupId)
              and t.dueDate is null and t.completedAt is null
            """)
    List<Task> undated(UUID userId, UUID groupId);

    List<Task> findByUserIdOrderByPositionAscCreatedAtAsc(UUID userId);

    long countByUserId(UUID userId);

    /** Apaga as tarefas pessoais (o banco apaga ocorrências, subtarefas e etiquetas ligadas). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Task t where t.userId = :userId")
    int deleteAllOfUser(UUID userId);

    @Query("select count(t) from Task t where (t.userId = :userId or t.groupId = :groupId) and t.completedAt is null")
    long countOpen(UUID userId, UUID groupId);

    @Query("select coalesce(max(t.position), -1) from Task t where t.userId = :userId or t.groupId = :groupId")
    int maxPosition(UUID userId, UUID groupId);

    @Query("select t.listId, count(t) from Task t where t.listId in :listIds and t.completedAt is null group by t.listId")
    List<Object[]> openByList(Collection<UUID> listIds);

    @Query("""
            select tag, count(t) from Task t join t.tagIds tag
            where tag in :tagIds and t.completedAt is null group by tag
            """)
    List<Object[]> openByTag(Collection<UUID> tagIds);

    /** Tarefas de grupo com algo para {@code userId} (responsável fixo, rodízio ou só numa ocorrência) no período. */
    @Query("""
            select t from Task t where t.groupId in (select m.groupId from GroupMember m where m.userId = :userId)
              and (t.assigneeId = :userId or :userId member of t.rotation
                   or exists (select 1 from TaskOccurrence o where o.taskId = t.id and o.assigneeId = :userId))
              and t.dueDate is not null
              and ((t.completedAt is null and t.dueDate <= :to and (t.repeatUntil is null or t.repeatUntil >= :lower))
                or exists (select 1 from TaskOccurrence o where o.taskId = t.id
                           and (o.date between :from and :to or o.movedTo between :from and :to)))
            """)
    List<Task> assignedAgenda(UUID userId, LocalDate lower, LocalDate from, LocalDate to);

    @Query("""
            select t from Task t where t.groupId in (select m.groupId from GroupMember m where m.userId = :userId)
              and t.assigneeId = :userId and t.dueDate is null and t.completedAt is null
            """)
    List<Task> assignedUndated(UUID userId);

    @Query("select t from Task t where t.groupId = :groupId and (t.assigneeId = :userId or :userId member of t.rotation)")
    List<Task> involving(UUID groupId, UUID userId);

    /** Hábitos em andamento, na ordem manual. */
    @Query("""
            select t from Task t where (t.userId = :userId or t.groupId = :groupId) and t.habit = true
              and t.completedAt is null and (t.repeatUntil is null or t.repeatUntil >= :since)
            order by t.position, t.createdAt
            """)
    List<Task> habits(UUID userId, UUID groupId, LocalDate since);

    /** Tarefas com lembrete que podem ter ocorrência de {@code from} a {@code to} (de todos os donos). */
    @Query("""
            select t from Task t where t.reminderMinutes is not null and t.dueDate is not null and t.completedAt is null
              and ((t.dueDate <= :to and (t.repeatUntil is null or t.repeatUntil >= :from))
                or exists (select 1 from TaskOccurrence o where o.taskId = t.id and o.movedTo between :from and :to))
            """)
    List<Task> withReminders(LocalDate from, LocalDate to);

    /** Grupos com tarefa que pode ter ocorrência pendente em {@code day}. */
    @Query("""
            select distinct t.groupId from Task t where t.groupId is not null and t.dueDate is not null
              and ((t.completedAt is null and t.dueDate <= :day and (t.repeatUntil is null or t.repeatUntil >= :day))
                or exists (select 1 from TaskOccurrence o where o.taskId = t.id and o.movedTo = :day))
            """)
    List<UUID> groupsWithTasksOn(LocalDate day);
}
