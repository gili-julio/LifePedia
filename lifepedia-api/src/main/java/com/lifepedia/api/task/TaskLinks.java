package com.lifepedia.api.task;

import com.fasterxml.jackson.annotation.JsonFormat;
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

/** O que outros módulos (ex.: frequência) leem de tarefas pessoais ligadas a eles. */
@Service
public class TaskLinks {

    private final TaskRepository tasks;
    private final TaskOccurrenceRepository occurrences;

    public TaskLinks(TaskRepository tasks, TaskOccurrenceRepository occurrences) {
        this.tasks = tasks;
        this.occurrences = occurrences;
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

    private static LinkedTask linked(Task task) {
        return new LinkedTask(task.getId(), task.getTitle(), task.getDueTime(), task.getRepeatUnit() != null);
    }
}
