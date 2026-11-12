package com.lifepedia.api.task;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lifepedia.api.task.TaskDtos.StatusRequest;
import com.lifepedia.api.task.TaskOccurrence.Status;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** O que outros módulos (ex.: frequência, notas) leem das tarefas ligadas a eles. */
@Service
public class TaskLinks {

    private final TaskRepository tasks;
    private final TaskOccurrenceRepository occurrences;
    private final TaskTagRepository tags;
    private final TaskService service;

    public TaskLinks(TaskRepository tasks, TaskOccurrenceRepository occurrences, TaskTagRepository tags, TaskService service) {
        this.tasks = tasks;
        this.occurrences = occurrences;
        this.tags = tags;
        this.service = service;
    }

    /** @param repeating tem regra de repetição */
    public record LinkedTask(UUID id, String title, @JsonFormat(pattern = "HH:mm") LocalTime dueTime, boolean repeating) {
    }

    /** Tarefa pessoal da pessoa (de grupo ou de outra pessoa: vazio). */
    @Transactional(readOnly = true)
    public Optional<LinkedTask> personal(UUID userId, UUID taskId) {
        return tasks.findById(taskId).filter(t -> userId.equals(t.getUserId())).map(TaskLinks::linked);
    }

    @Transactional(readOnly = true)
    public Map<UUID, LinkedTask> personal(UUID userId, Collection<UUID> taskIds) {
        return tasks.findAllById(taskIds).stream().filter(t -> userId.equals(t.getUserId()))
                .collect(Collectors.toMap(Task::getId, TaskLinks::linked));
    }

    /** Pessoais em aberto com data, para escolher qual ligar. */
    @Transactional(readOnly = true)
    public List<LinkedTask> open(UUID userId) {
        return tasks.findByUserIdOrderByPositionAscCreatedAtAsc(userId).stream()
                .filter(t -> t.getCompletedAt() == null && t.getDueDate() != null)
                .map(TaskLinks::linked)
                .sorted(Comparator.comparing(LinkedTask::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Tarefa do dono (pessoal da pessoa, ou do grupo quando {@code groupId} vem), para ligar a uma nota. */
    @Transactional(readOnly = true)
    public Optional<LinkedTask> owned(UUID userId, UUID groupId, UUID taskId) {
        return tasks.findById(taskId).filter(t -> ownedBy(t, userId, groupId)).map(TaskLinks::linked);
    }

    @Transactional(readOnly = true)
    public Map<UUID, LinkedTask> owned(UUID userId, UUID groupId, Collection<UUID> taskIds) {
        return tasks.findAllById(taskIds).stream().filter(t -> ownedBy(t, userId, groupId))
                .collect(Collectors.toMap(Task::getId, TaskLinks::linked));
    }

    /** Em aberto do dono (com ou sem data), por título. */
    @Transactional(readOnly = true)
    public List<LinkedTask> openOwned(UUID userId, UUID groupId) {
        List<Task> list = groupId != null ? tasks.findByGroupIdAndCompletedAtIsNull(groupId)
                : tasks.findByUserIdOrderByPositionAscCreatedAtAsc(userId);
        return list.stream()
                .filter(t -> t.getCompletedAt() == null)
                .map(TaskLinks::linked)
                .sorted(Comparator.comparing(LinkedTask::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Dias (pela regra) em que cada tarefa pessoal da pessoa foi concluída, de {@code from} a {@code to}. */
    @Transactional(readOnly = true)
    public Map<UUID, Set<LocalDate>> doneDates(UUID userId, Collection<UUID> taskIds, LocalDate from, LocalDate to) {
        Set<UUID> own = personal(userId, taskIds).keySet();
        if (own.isEmpty()) {
            return Map.of();
        }
        return occurrences.between(own, from, to).stream()
                .filter(o -> o.getStatus() == Status.DONE)
                .collect(Collectors.groupingBy(TaskOccurrence::getTaskId,
                        Collectors.mapping(TaskOccurrence::getDate, Collectors.toSet())));
    }

    /**
     * Tarefa pessoal criada por outro módulo (ex.: issue do GitLab), com a etiqueta {@code tagName} (criada se faltar).
     * Vazio se a pessoa chegou ao limite de tarefas abertas.
     */
    @Transactional
    public Optional<UUID> createPersonal(UUID userId, String title, String notes, LocalDate dueDate, String tagName, String tagColor) {
        if (tasks.countOpen(userId, null) >= TaskService.MAX_OPEN) return Optional.empty();
        Task task = new Task(TaskOwner.personal(userId), userId, tasks.maxPosition(userId, null) + 1);
        task.setDetails(cut(title, 200), notes == null ? null : cut(notes, 10000), Priority.NONE, null, false);
        task.setSchedule(dueDate, null, null);
        UUID tag = tag(userId, tagName, tagColor);
        if (tag != null) task.setTagIds(Set.of(tag));
        return Optional.of(tasks.save(task).getId());
    }

    /** Conclui a tarefa pessoal sem repetição (já concluída ou de outra pessoa: nada muda). */
    @Transactional
    public void completePersonal(UUID userId, UUID taskId) {
        tasks.findById(taskId)
                .filter(t -> userId.equals(t.getUserId()) && t.getRepeatUnit() == null && t.getCompletedAt() == null)
                .ifPresent(t -> service.setStatus(TaskScope.personal(userId), t.getId(),
                        new StatusRequest(t.getDueDate(), Status.DONE)));
    }

    /** Quais das tarefas pessoais já estão concluídas. */
    @Transactional(readOnly = true)
    public Set<UUID> completed(UUID userId, Collection<UUID> taskIds) {
        return tasks.findAllById(taskIds).stream().filter(t -> userId.equals(t.getUserId()) && t.getCompletedAt() != null)
                .map(Task::getId).collect(Collectors.toSet());
    }

    private UUID tag(UUID userId, String name, String color) {
        if (name == null || name.isBlank()) return null;
        String clean = cut(name.trim(), 40);
        List<TaskTag> owned = tags.owned(userId, null);
        return owned.stream().filter(t -> t.getName().equalsIgnoreCase(clean)).map(TaskTag::getId).findFirst()
                .orElseGet(() -> owned.size() >= TaskLabelService.MAX_TAGS ? null
                        : tags.save(new TaskTag(TaskOwner.personal(userId), clean, color)).getId());
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static boolean ownedBy(Task task, UUID userId, UUID groupId) {
        return groupId != null ? groupId.equals(task.getGroupId()) : userId.equals(task.getUserId());
    }

    private static LinkedTask linked(Task task) {
        return new LinkedTask(task.getId(), task.getTitle(), task.getDueTime(), task.getRepeatUnit() != null);
    }
}
