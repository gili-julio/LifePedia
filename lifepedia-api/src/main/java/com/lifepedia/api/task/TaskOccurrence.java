package com.lifepedia.api.task;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** O que mudou numa ocorrência (só existe quando algo difere da tarefa). */
@Entity
@Table(name = "task_occurrences")
public class TaskOccurrence extends BaseEntity {

    public enum Status { PENDING, DONE, SKIPPED }

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    /** Dia pela regra (sem repetição: o dia da tarefa; sem data: nulo). */
    @Column(name = "occurrence_date")
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Status status = Status.PENDING;

    @Column(name = "moved_to")
    private LocalDate movedTo;

    @Column(length = 200)
    private String title;

    @Column(length = 10000)
    private String notes;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Enumerated(EnumType.STRING)
    @Column(length = 6)
    private Priority priority;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    /** Responsável só nesta (ou o de quando foi resolvida); nulo = o da tarefa. */
    @Column(name = "assignee_id")
    private UUID assigneeId;

    @ElementCollection
    @CollectionTable(name = "task_subtask_checks", joinColumns = @JoinColumn(name = "occurrence_id"))
    @Column(name = "subtask_id")
    private Set<UUID> checkedSubtasks = new HashSet<>();

    /** "Cada um conclui a sua": quem já fez a sua parte (a ocorrência fica concluída quando todos fizerem). */
    @ElementCollection
    @CollectionTable(name = "task_occurrence_done", joinColumns = @JoinColumn(name = "occurrence_id"))
    @Column(name = "user_id")
    private Set<UUID> doneBy = new HashSet<>();

    protected TaskOccurrence() {
    }

    public TaskOccurrence(UUID taskId, LocalDate date) {
        this.taskId = taskId;
        this.date = date;
    }

    public void complete(OffsetDateTime at, UUID by) {
        status = Status.DONE;
        completedAt = at;
        completedBy = by;
    }

    public void setStatus(Status status) {
        this.status = status;
        if (status != Status.DONE) {
            completedAt = null;
            completedBy = null;
        }
    }

    /** Edição "só esta"; nulo = igual à tarefa. */
    public void override(String title, String notes, LocalTime dueTime, Priority priority) {
        this.title = title;
        this.notes = notes;
        this.dueTime = dueTime;
        this.priority = priority;
    }

    /** Nada diferente da tarefa: a linha pode sair. */
    public boolean isPlain() {
        return status == Status.PENDING && movedTo == null && title == null && notes == null && dueTime == null
                && priority == null && assigneeId == null && checkedSubtasks.isEmpty() && doneBy.isEmpty();
    }

    public UUID getTaskId() {
        return taskId;
    }

    public void moveTo(UUID taskId) {
        this.taskId = taskId;
        checkedSubtasks.clear();
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getMovedTo() {
        return movedTo;
    }

    public void setMovedTo(LocalDate movedTo) {
        this.movedTo = movedTo;
    }

    public String getTitle() {
        return title;
    }

    public String getNotes() {
        return notes;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public Priority getPriority() {
        return priority;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public UUID getCompletedBy() {
        return completedBy;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
    }

    public Set<UUID> getCheckedSubtasks() {
        return checkedSubtasks;
    }

    public Set<UUID> getDoneBy() {
        return doneBy;
    }
}
