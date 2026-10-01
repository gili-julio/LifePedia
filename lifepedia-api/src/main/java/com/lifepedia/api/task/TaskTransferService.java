package com.lifepedia.api.task;

import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.task.TaskDtos.LabelData;
import com.lifepedia.api.task.TaskDtos.OccurrenceData;
import com.lifepedia.api.task.TaskDtos.TaskData;
import com.lifepedia.api.task.TaskOccurrence.Status;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Tarefas pessoais no arquivo da conta: exporta e importa (listas/etiquetas iguais pelo nome são reaproveitadas). */
@Service
public class TaskTransferService {

    private final TaskRepository tasks;
    private final TaskOccurrenceRepository occurrences;
    private final TaskSubtaskRepository subtasks;
    private final TaskListRepository lists;
    private final TaskTagRepository tags;

    public TaskTransferService(TaskRepository tasks, TaskOccurrenceRepository occurrences, TaskSubtaskRepository subtasks,
                               TaskListRepository lists, TaskTagRepository tags) {
        this.tasks = tasks;
        this.occurrences = occurrences;
        this.subtasks = subtasks;
        this.lists = lists;
        this.tags = tags;
    }

    public record Exported(List<LabelData> lists, List<LabelData> tags, List<TaskData> tasks) {
    }

    /** @param removed tarefas apagadas antes (REPLACE) */
    public record Outcome(int created, int skipped, int removed, int labelsCreated) {
    }

    /** @param fixedRef ref do fixo no arquivo, pelo id */
    @Transactional(readOnly = true)
    public Exported export(UUID userId, Function<UUID, String> fixedRef) {
        List<Task> own = tasks.findByUserIdOrderByPositionAscCreatedAtAsc(userId);
        List<UUID> ids = own.stream().map(Task::getId).toList();
        Map<UUID, List<TaskSubtask>> checklists = ids.isEmpty() ? Map.of() : subtasks.findByTaskIdInOrderByPositionAsc(ids)
                .stream().collect(Collectors.groupingBy(TaskSubtask::getTaskId));
        Map<UUID, List<TaskOccurrence>> rows = ids.isEmpty() ? Map.of() : occurrences.findByTaskIdIn(ids).stream()
                .collect(Collectors.groupingBy(TaskOccurrence::getTaskId));
        List<TaskData> data = new ArrayList<>();
        for (Task t : own) {
            List<UUID> order = checklists.getOrDefault(t.getId(), List.of()).stream().map(TaskSubtask::getId).toList();
            List<OccurrenceData> changed = rows.getOrDefault(t.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(TaskOccurrence::getDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .map(o -> new OccurrenceData(o.getDate(), o.getStatus(), o.getMovedTo(), o.getTitle(), o.getNotes(),
                            o.getDueTime(), o.getPriority(), o.getCompletedAt(),
                            o.getCheckedSubtasks().stream().map(order::indexOf).filter(i -> i >= 0).sorted().toList()))
                    .toList();
            data.add(new TaskData(t.getTitle(), t.getNotes(), t.getPriority(), t.getDueDate(), t.getDueTime(),
                    t.getDurationMinutes(), t.getReminderMinutes(), ref(t.getListId()),
                    t.getTagIds().stream().map(UUID::toString).sorted().toList(), t.isHabit(), TaskService.repeat(t.rule()),
                    t.getCompletedAt(), t.getRecurringEntryId() == null ? null : fixedRef.apply(t.getRecurringEntryId()),
                    checklists.getOrDefault(t.getId(), List.of()).stream().map(TaskSubtask::getTitle).toList(), changed));
        }
        return new Exported(
                lists.owned(userId, null).stream().map(l -> new LabelData(ref(l.getId()), l.getName(), l.getColor())).toList(),
                tags.owned(userId, null).stream().map(g -> new LabelData(ref(g.getId()), g.getName(), g.getColor())).toList(),
                data);
    }

    /** Refs, regras de cada tarefa e limite de abertas; erro (400) com o nome da tarefa. */
    public static void validate(List<LabelData> listData, List<LabelData> tagData, List<TaskData> data, Set<String> fixedRefs) {
        Set<String> listRefs = labelRefs(listData, "Lista");
        Set<String> tagRefs = labelRefs(tagData, "Etiqueta");
        long open = data.stream().filter(t -> t.completedAt() == null).count();
        if (open > TaskService.MAX_OPEN) throw bad("Mais de " + TaskService.MAX_OPEN + " tarefas abertas no arquivo.");
        for (TaskData t : data) {
            String name = "Tarefa \"" + t.title() + "\": ";
            if (t.dueTime() != null && t.dueDate() == null) throw bad(name + "horário sem dia.");
            if (t.durationMinutes() != null && t.dueTime() == null) throw bad(name + "duração sem horário.");
            if (t.reminderMinutes() != null && t.dueTime() == null) throw bad(name + "lembrete sem horário.");
            Recurrence rule;
            try {
                rule = TaskService.rule(t.repeat(), t.dueDate());
            } catch (BusinessException e) {
                throw bad(name + lower(e.getMessage()));
            }
            if (Boolean.TRUE.equals(t.habit()) && (rule == null || rule.fromCompletion())) {
                throw bad(name + "hábito precisa de repetição em dias fixos.");
            }
            if (t.list() != null && !listRefs.contains(t.list())) throw bad(name + "usa uma lista que não está no arquivo.");
            if (t.tags() != null && !tagRefs.containsAll(t.tags())) throw bad(name + "usa uma etiqueta que não está no arquivo.");
            if (t.fixed() != null && !fixedRefs.contains(t.fixed())) throw bad(name + "usa um fixo que não está no arquivo.");
            Set<LocalDate> dates = new HashSet<>();
            boolean undated = false;
            for (OccurrenceData o : t.occurrences() == null ? List.<OccurrenceData>of() : t.occurrences()) {
                boolean fresh = o.date() == null ? !undated : dates.add(o.date());
                if (o.date() == null) undated = true;
                if (!fresh || (o.date() == null) != (t.dueDate() == null)) throw bad(name + "ocorrência inválida.");
            }
        }
    }

    /**
     * Importa as tarefas (já validadas). REPLACE apaga as tarefas, listas e etiquetas pessoais antes; MERGE pula tarefa
     * igual a uma que já existe (título, dia, horário e repetição).
     *
     * @param fixedByRef id do fixo (criado ou reaproveitado) pelo ref do arquivo
     */
    @Transactional
    public Outcome importTasks(UUID userId, List<LabelData> listData, List<LabelData> tagData, List<TaskData> data,
                               boolean replace, boolean apply, Map<String, UUID> fixedByRef) {
        TaskOwner owner = TaskOwner.personal(userId);
        int removed = 0;
        if (replace) {
            removed = (int) tasks.countByUserId(userId);
            if (apply) {
                tasks.deleteAllOfUser(userId);
                lists.deleteAllOfUser(userId);
                tags.deleteAllOfUser(userId);
            }
        }
        int[] labelsCreated = {0};
        Map<String, UUID> listByRef = labels(listData, replace ? List.of() : lists.owned(userId, null).stream()
                .map(l -> Map.entry(l.getName(), l.getId())).toList(), apply, labelsCreated,
                l -> lists.save(new TaskList(owner, l.name().trim(), l.color().toLowerCase(Locale.ROOT))).getId());
        Map<String, UUID> tagByRef = labels(tagData, replace ? List.of() : tags.owned(userId, null).stream()
                .map(g -> Map.entry(g.getName(), g.getId())).toList(), apply, labelsCreated,
                g -> tags.save(new TaskTag(owner, g.name().trim(), g.color().toLowerCase(Locale.ROOT))).getId());

        Map<String, Integer> existing = new HashMap<>();
        if (!replace) {
            for (Task t : tasks.findByUserIdOrderByPositionAscCreatedAtAsc(userId)) {
                existing.merge(key(t.getTitle(), t.getDueDate(), String.valueOf(t.getDueTime()), TaskService.repeat(t.rule())), 1, Integer::sum);
            }
            long open = tasks.countOpen(userId, null) + data.stream().filter(t -> t.completedAt() == null).count();
            if (open > TaskService.MAX_OPEN) throw bad("Ficaria com mais de " + TaskService.MAX_OPEN + " tarefas abertas.");
        }
        int position = replace ? 0 : tasks.maxPosition(userId, null) + 1;
        int created = 0, skipped = 0;
        for (TaskData t : data) {
            String key = key(t.title(), t.dueDate(), String.valueOf(t.dueTime()), t.repeat());
            Integer same = existing.get(key);
            if (same != null && same > 0) {
                existing.put(key, same - 1);
                skipped++;
                continue;
            }
            created++;
            if (apply) save(userId, t, position++, listByRef, tagByRef, fixedByRef);
        }
        return new Outcome(created, skipped, removed, labelsCreated[0]);
    }

    private void save(UUID userId, TaskData t, int position, Map<String, UUID> listByRef, Map<String, UUID> tagByRef,
                      Map<String, UUID> fixedByRef) {
        Recurrence rule = TaskService.rule(t.repeat(), t.dueDate());
        Task task = new Task(TaskOwner.personal(userId), userId, position);
        task.setDetails(t.title().trim(), blankToNull(t.notes()), t.priority() == null ? Priority.NONE : t.priority(),
                t.list() == null ? null : listByRef.get(t.list()), Boolean.TRUE.equals(t.habit()));
        task.setSchedule(t.dueDate(), t.dueTime(), t.durationMinutes());
        task.setReminder(t.reminderMinutes());
        task.setRecurringEntryId(t.fixed() == null ? null : fixedByRef.get(t.fixed()));
        task.setRepeat(rule);
        task.setTagIds(t.tags() == null ? Set.of() : t.tags().stream().map(tagByRef::get).collect(Collectors.toSet()));
        task.setCompletedAt(t.completedAt());
        tasks.save(task);
        List<TaskSubtask> items = new ArrayList<>();
        List<String> titles = t.subtasks() == null ? List.of() : t.subtasks();
        for (int i = 0; i < titles.size(); i++) items.add(new TaskSubtask(task.getId(), titles.get(i).trim(), i));
        subtasks.saveAll(items);
        List<TaskOccurrence> rows = new ArrayList<>();
        for (OccurrenceData o : t.occurrences() == null ? List.<OccurrenceData>of() : t.occurrences()) {
            TaskOccurrence row = new TaskOccurrence(task.getId(), o.date());
            row.override(blankToNull(o.title()), blankToNull(o.notes()), o.dueTime(), o.priority());
            row.setMovedTo(o.movedTo());
            if (o.status() == Status.DONE) row.complete(o.completedAt() != null ? o.completedAt() : OffsetDateTime.now(), userId);
            else row.setStatus(o.status());
            for (Integer index : o.checked() == null ? List.<Integer>of() : o.checked()) {
                if (index < items.size()) row.getCheckedSubtasks().add(items.get(index).getId());
            }
            if (!row.isPlain()) rows.add(row);
        }
        occurrences.saveAll(rows);
    }

    /** Ref → id: reaproveita a de mesmo nome; cria as outras (na prévia, um id qualquer). */
    private static Map<String, UUID> labels(List<LabelData> data, Collection<Map.Entry<String, UUID>> current, boolean apply,
                                            int[] created, Function<LabelData, UUID> create) {
        Map<String, UUID> byName = new HashMap<>();
        current.forEach(e -> byName.put(normalize(e.getKey()), e.getValue()));
        Map<String, UUID> byRef = new HashMap<>();
        for (LabelData l : data) {
            UUID id = byName.get(normalize(l.name()));
            if (id == null) {
                id = apply ? create.apply(l) : UUID.randomUUID();
                byName.put(normalize(l.name()), id);
                created[0]++;
            }
            byRef.put(l.ref(), id);
        }
        return byRef;
    }

    private static Set<String> labelRefs(List<LabelData> data, String kind) {
        Set<String> refs = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (LabelData l : data) {
            if (!refs.add(l.ref()) || !names.add(normalize(l.name()))) throw bad(kind + " repetida no arquivo: " + l.name() + ".");
        }
        return refs;
    }

    private static String key(String title, LocalDate dueDate, String dueTime, TaskDtos.RepeatRule repeat) {
        String rule = repeat == null ? "" : repeat.unit() + "/" + repeat.interval();
        return String.join("|", normalize(title), String.valueOf(dueDate), dueTime, rule);
    }

    private static String ref(UUID id) {
        return id == null ? null : id.toString();
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static String lower(String message) {
        return message.isEmpty() ? message : Character.toLowerCase(message.charAt(0)) + message.substring(1);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BusinessException bad(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}
