package com.lifepedia.api.task;

import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.PageResponse;
import com.lifepedia.api.shared.Paging;
import com.lifepedia.api.task.TaskDtos.Agenda;
import com.lifepedia.api.task.TaskDtos.Day;
import com.lifepedia.api.task.TaskDtos.HabitDay;
import com.lifepedia.api.task.TaskDtos.HabitSummary;
import com.lifepedia.api.task.TaskDtos.HistoryItem;
import com.lifepedia.api.task.TaskDtos.Sort;
import com.lifepedia.api.task.TaskDtos.SubtaskState;
import com.lifepedia.api.task.TaskDtos.TaskItem;
import com.lifepedia.api.task.TaskOccurrence.Status;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leitura: agenda por dia (com atrasadas), tarefas sem data e histórico de concluídas. Ordenação sempre aqui.
 * Na visão pessoal (sem filtro de lista/etiqueta) entram também as ocorrências de grupo em que a pessoa é a responsável.
 */
@Service
public class TaskAgendaService {

    static final int MAX_RANGE_DAYS = 62;
    private static final Map<String, String> HISTORY_SORT = Map.of("completedAt", "o.completedAt");

    private final TaskRepository tasks;
    private final TaskOccurrenceRepository occurrences;
    private final TaskSubtaskRepository subtasks;
    private final TaskCommentRepository comments;
    private final GroupMemberRepository members;
    private final TaskService service;

    public TaskAgendaService(TaskRepository tasks, TaskOccurrenceRepository occurrences,
                             TaskSubtaskRepository subtasks, TaskCommentRepository comments,
                             GroupMemberRepository members, TaskService service) {
        this.tasks = tasks;
        this.occurrences = occurrences;
        this.subtasks = subtasks;
        this.comments = comments;
        this.members = members;
        this.service = service;
    }

    /** Filtro opcional por lista, etiqueta e responsável (este só em grupo). */
    public record Filter(UUID listId, UUID tagId, UUID assigneeId) {

        public static final Filter NONE = new Filter(null, null, null);

        boolean accepts(Task task) {
            return (listId == null || listId.equals(task.getListId()))
                    && (tagId == null || task.getTagIds().contains(tagId));
        }

        boolean labeled() {
            return listId != null || tagId != null;
        }
    }

    /**
     * Dias de {@code from} a {@code to}, cada um com as ocorrências (pendentes e concluídas; puladas não aparecem).
     *
     * @param withOverdue inclui as pendentes antes de {@code from} (repetidas: até {@link TaskService#OVERDUE_DAYS} dias)
     */
    @Transactional(readOnly = true)
    public Agenda agenda(TaskScope scope, LocalDate from, LocalDate to, boolean withOverdue, Filter filter, Sort sort) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Período inválido (até " + MAX_RANGE_DAYS + " dias).");
        }
        TaskOwner owner = scope.owner();
        LocalDate today = service.today();
        LocalDate lower = withOverdue ? min(from, today.minusDays(TaskService.OVERDUE_DAYS)) : from;
        List<Task> found = new ArrayList<>(tasks.agenda(owner.userId(), owner.groupId(), lower, from, to).stream()
                .filter(filter::accepts).toList());
        if (!scope.isGroup() && !filter.labeled()) found.addAll(tasks.assignedAgenda(scope.userId(), lower, from, to));
        Context context = context(scope, found);
        Map<UUID, Map<LocalDate, TaskOccurrence>> rows = rowsByTask(found.isEmpty() ? List.of()
                : occurrences.window(ids(found), lower, to));

        Map<LocalDate, List<Entry>> byDay = new LinkedHashMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) byDay.put(day, new ArrayList<>());
        List<Entry> overdue = new ArrayList<>();
        for (Task task : found) {
            Map<LocalDate, TaskOccurrence> taskRows = rows.getOrDefault(task.getId(), Map.of());
            for (LocalDate date : dates(task, taskRows, lower, from, to)) {
                TaskOccurrence row = taskRows.get(date);
                Status status = row == null ? Status.PENDING : row.getStatus();
                if (status == Status.SKIPPED) continue;
                UUID assignee = TaskService.assignee(task, date, row);
                if (!context.shows(task, assignee, filter)) continue;
                LocalDate shown = row != null && row.getMovedTo() != null ? row.getMovedTo() : date;
                boolean late = status == Status.PENDING && shown.isBefore(today);
                Entry entry = context.entry(task, date, row, assignee, late);
                if (!shown.isBefore(from) && !shown.isAfter(to)) byDay.get(shown).add(entry);
                else if (withOverdue && late && shown.isBefore(from)) overdue.add(entry);
            }
        }
        Comparator<Entry> order = order(sort);
        List<Day> days = byDay.entrySet().stream()
                .map(e -> new Day(e.getKey(), items(e.getValue(),
                        Comparator.comparing((Entry x) -> x.item.status() != Status.PENDING).thenComparing(order))))
                .toList();
        return new Agenda(today, items(overdue, Comparator.comparing((Entry x) -> x.item.scheduledDate()).thenComparing(order)), days);
    }

    /** Pendentes sem data. */
    @Transactional(readOnly = true)
    public List<TaskItem> undated(TaskScope scope, Filter filter, Sort sort) {
        TaskOwner owner = scope.owner();
        List<Task> found = new ArrayList<>(tasks.undated(owner.userId(), owner.groupId()).stream().filter(filter::accepts).toList());
        if (!scope.isGroup() && !filter.labeled()) found.addAll(tasks.assignedUndated(scope.userId()));
        Context context = context(scope, found);
        Map<UUID, TaskOccurrence> rows = found.isEmpty() ? Map.of() : occurrences.undated(ids(found)).stream()
                .collect(Collectors.toMap(TaskOccurrence::getTaskId, Function.identity()));
        List<Entry> entries = new ArrayList<>();
        for (Task task : found) {
            TaskOccurrence row = rows.get(task.getId());
            UUID assignee = TaskService.assignee(task, null, row);
            if (context.shows(task, assignee, filter)) entries.add(context.entry(task, null, row, assignee, false));
        }
        return items(entries, order(sort));
    }

    /** Hábitos em andamento com sequência, % do mês e os últimos 7 dias. */
    @Transactional(readOnly = true)
    public List<HabitSummary> habits(TaskScope scope) {
        TaskOwner owner = scope.owner();
        LocalDate today = service.today();
        LocalDate week = today.minusDays(6);
        List<Task> found = tasks.habits(owner.userId(), owner.groupId(), week);
        LocalDate lower = today.minusDays(HabitStats.LOOKBACK_DAYS);
        Map<UUID, Map<LocalDate, TaskOccurrence>> rows = rowsByTask(found.isEmpty() ? List.of()
                : occurrences.between(ids(found), lower, today));
        List<HabitSummary> result = new ArrayList<>();
        for (Task task : found) {
            Map<LocalDate, TaskOccurrence> taskRows = rows.getOrDefault(task.getId(), Map.of());
            List<LocalDate> dates = habitDates(task, lower, today);
            // Série que já acabou (por quantidade) sai quando não tem mais nada na semana.
            if (dates.stream().noneMatch(d -> !d.isBefore(week)) && task.rule().between(today, today.plusYears(1)).isEmpty()) continue;
            HabitStats stats = HabitStats.of(dates, d -> habitDay(taskRows.get(d), d, today), today.withDayOfMonth(1));
            Set<LocalDate> scheduled = Set.copyOf(dates);
            List<HabitDay> days = new ArrayList<>();
            for (LocalDate day = week; !day.isAfter(today); day = day.plusDays(1)) {
                days.add(new HabitDay(day, scheduled.contains(day) ? habitDay(taskRows.get(day), day, today) : null));
            }
            result.add(new HabitSummary(task.getId(), task.getTitle(), task.getListId(), task.getGroupId(),
                    TaskService.repeat(task.rule()), task.getDueDate(), stats.streak(), stats.best(), stats.monthRate(),
                    stats.monthDone(), stats.monthDue(), days));
        }
        return result;
    }

    /** Ocorrência pendente mostrada entre {@code from} e {@code to}, com horário e responsável (para os avisos). */
    public record Pending(Task task, LocalDate date, LocalDate shown, LocalTime time, String title, UUID assignee) {
    }

    @Transactional(readOnly = true)
    public List<Pending> pending(List<Task> found, LocalDate from, LocalDate to) {
        Map<UUID, Map<LocalDate, TaskOccurrence>> rows = rowsByTask(found.isEmpty() ? List.of()
                : occurrences.window(ids(found), from, to));
        List<Pending> result = new ArrayList<>();
        for (Task task : found) {
            Map<LocalDate, TaskOccurrence> taskRows = rows.getOrDefault(task.getId(), Map.of());
            for (LocalDate date : dates(task, taskRows, from, from, to)) {
                TaskOccurrence row = taskRows.get(date);
                if (row != null && row.getStatus() != Status.PENDING) continue;
                LocalDate shown = row != null && row.getMovedTo() != null ? row.getMovedTo() : date;
                if (shown.isBefore(from) || shown.isAfter(to)) continue;
                result.add(new Pending(task, date, shown,
                        row != null && row.getDueTime() != null ? row.getDueTime() : task.getDueTime(),
                        row != null && row.getTitle() != null ? row.getTitle() : task.getTitle(),
                        task.getGroupId() == null ? null : TaskService.assignee(task, date, row)));
            }
        }
        return result;
    }

    /** Ocorrências concluídas, da mais recente para a mais antiga. */
    @Transactional(readOnly = true)
    public PageResponse<HistoryItem> history(TaskScope scope, int page, int size, String sort) {
        TaskOwner owner = scope.owner();
        var pageable = Paging.pageable(page, size, sort, HISTORY_SORT, "completedAt,desc", "o.id");
        return PageResponse.of(occurrences.history(owner.userId(), owner.groupId(), Status.DONE, pageable), done -> {
            Map<UUID, Task> byId = tasks.findAllById(done.stream().map(TaskOccurrence::getTaskId).collect(Collectors.toSet()))
                    .stream().collect(Collectors.toMap(Task::getId, Function.identity()));
            return done.stream().map(o -> {
                Task task = byId.get(o.getTaskId());
                return new HistoryItem(task.getId(), o.getDate(), o.getTitle() != null ? o.getTitle() : task.getTitle(),
                        task.getListId(), o.getCompletedAt(), task.getRepeatUnit() != null, o.getCompletedBy());
            }).toList();
        });
    }

    /** Dias do hábito de {@code lower} até hoje (hábito sempre tem regra em dias fixos). */
    private static List<LocalDate> habitDates(Task task, LocalDate lower, LocalDate today) {
        Recurrence rule = task.rule();
        if (rule == null || rule.fromCompletion()) return List.of();
        return rule.between(lower, today).stream().map(Recurrence.Occurrence::date).toList();
    }

    /** Pendente de dia que passou (e não adiada para frente) conta como perdida. */
    private static HabitStats.Day habitDay(TaskOccurrence row, LocalDate date, LocalDate today) {
        Status status = row == null ? Status.PENDING : row.getStatus();
        if (status == Status.DONE) return HabitStats.Day.DONE;
        if (status == Status.SKIPPED) return HabitStats.Day.SKIPPED;
        LocalDate shown = row != null && row.getMovedTo() != null ? row.getMovedTo() : date;
        return shown.isBefore(today) ? HabitStats.Day.MISSED : HabitStats.Day.PENDING;
    }

    /** Dias (pela regra) a considerar: os gerados, os adiados para o período e os concluídos nele. */
    private static Collection<LocalDate> dates(Task task, Map<LocalDate, TaskOccurrence> rows,
                                               LocalDate lower, LocalDate from, LocalDate to) {
        Set<LocalDate> result = new LinkedHashSet<>();
        if (task.isSingleSlot()) {
            if (task.getCompletedAt() == null || rows.containsKey(task.getDueDate())) result.add(task.getDueDate());
        } else {
            task.rule().between(lower, to).forEach(o -> result.add(o.date()));
        }
        for (TaskOccurrence row : rows.values()) {
            boolean movedIn = row.getMovedTo() != null && !row.getMovedTo().isBefore(from) && !row.getMovedTo().isAfter(to);
            boolean doneIn = row.getStatus() == Status.DONE && !row.getDate().isBefore(from) && !row.getDate().isAfter(to);
            if ((movedIn && !task.isSingleSlot()) || doneIn) result.add(row.getDate());
        }
        return result;
    }

    /**
     * Ocorrência pronta para ordenar (posição e criação da tarefa desempatam).
     *
     * @param foreign tarefa de grupo na visão pessoal (fica depois das pessoais na ordem manual)
     */
    private record Entry(TaskItem item, boolean foreign, int position, long created) {
    }

    /** O que a montagem das linhas precisa além da tarefa: checklists, comentários e permissões. */
    private final class Context {

        private final TaskScope scope;
        private final Map<UUID, List<TaskSubtask>> checklists;
        private final Map<UUID, Long> commentCounts;
        /** Visão pessoal: grupos em que a pessoa é administradora. */
        private final Set<UUID> adminGroups;
        private final Map<UUID, Integer> streaks = new HashMap<>();

        Context(TaskScope scope, List<Task> found) {
            this.scope = scope;
            this.checklists = found.isEmpty() ? Map.of() : subtasks.findByTaskIdInOrderByPositionAsc(ids(found)).stream()
                    .collect(Collectors.groupingBy(TaskSubtask::getTaskId));
            List<UUID> groupTasks = found.stream().filter(t -> t.getGroupId() != null).map(Task::getId).toList();
            this.commentCounts = new HashMap<>();
            if (!groupTasks.isEmpty()) {
                for (Object[] row : comments.countByTasks(groupTasks)) commentCounts.put((UUID) row[0], (Long) row[1]);
            }
            boolean foreign = !scope.isGroup() && found.stream().anyMatch(t -> t.getGroupId() != null);
            this.adminGroups = !foreign ? Set.of() : members.findByUserId(scope.userId()).stream()
                    .filter(GroupMember::isAdmin).map(GroupMember::getGroupId).collect(Collectors.toSet());
            List<Task> habits = found.stream().filter(Task::isHabit).toList();
            if (!habits.isEmpty()) {
                LocalDate today = service.today();
                LocalDate lower = today.minusDays(HabitStats.LOOKBACK_DAYS);
                Map<UUID, Map<LocalDate, TaskOccurrence>> rows = rowsByTask(occurrences.between(ids(habits), lower, today));
                for (Task task : habits) {
                    Map<LocalDate, TaskOccurrence> taskRows = rows.getOrDefault(task.getId(), Map.of());
                    streaks.put(task.getId(), HabitStats.of(habitDates(task, lower, today),
                            d -> habitDay(taskRows.get(d), d, today), today.withDayOfMonth(1)).streak());
                }
            }
        }

        boolean foreign(Task task) {
            return !scope.isGroup() && task.getGroupId() != null;
        }

        /** Visão pessoal: de grupo só as ocorrências da pessoa. Grupo: filtro de responsável. */
        boolean shows(Task task, UUID assignee, Filter filter) {
            if (foreign(task)) return scope.userId().equals(assignee);
            return filter.assigneeId() == null || filter.assigneeId().equals(assignee);
        }

        boolean canDelete(Task task) {
            if (!foreign(task)) return scope.canDelete(task);
            return adminGroups.contains(task.getGroupId()) || scope.userId().equals(task.getCreatedBy());
        }

        Entry entry(Task task, LocalDate date, TaskOccurrence row, UUID assignee, boolean late) {
            Set<UUID> checked = row == null ? Set.of() : row.getCheckedSubtasks();
            List<SubtaskState> states = checklists.getOrDefault(task.getId(), List.of()).stream()
                    .map(s -> new SubtaskState(s.getId(), s.getTitle(), checked.contains(s.getId()))).toList();
            boolean edited = row != null && (row.getTitle() != null || row.getNotes() != null || row.getDueTime() != null
                    || row.getPriority() != null);
            TaskItem item = new TaskItem(task.getId(), date,
                    row != null && row.getMovedTo() != null ? row.getMovedTo() : date,
                    row != null && row.getTitle() != null ? row.getTitle() : task.getTitle(),
                    row != null && row.getNotes() != null ? row.getNotes() : task.getNotes(),
                    row != null && row.getPriority() != null ? row.getPriority() : task.getPriority(),
                    row != null && row.getDueTime() != null ? row.getDueTime() : task.getDueTime(),
                    task.getDurationMinutes(), row == null ? Status.PENDING : row.getStatus(), late,
                    row == null ? null : row.getCompletedAt(), task.getListId(), Set.copyOf(task.getTagIds()), states,
                    TaskService.repeat(task.rule()), task.isHabit(), edited, task.getGroupId(), assignee,
                    !task.getRotation().isEmpty(), task.getCreatedBy(), row == null ? null : row.getCompletedBy(),
                    commentCounts.getOrDefault(task.getId(), 0L), canDelete(task), streaks.get(task.getId()),
                    task.getReminderMinutes(), task.getRecurringEntryId());
            return new Entry(item, foreign(task), task.getPosition(), task.getCreatedAt().toInstant().toEpochMilli());
        }
    }

    private Context context(TaskScope scope, List<Task> found) {
        return new Context(scope, found);
    }

    private static Comparator<Entry> order(Sort sort) {
        Comparator<Entry> manual = Comparator.comparing(Entry::foreign)
                .thenComparingInt(Entry::position).thenComparingLong(Entry::created);
        Comparator<Entry> time = Comparator.comparing((Entry e) -> e.item.dueTime(),
                Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()));
        return switch (sort == null ? Sort.MANUAL : sort) {
            case MANUAL -> manual;
            case TIME -> time.thenComparing(manual);
            case PRIORITY -> Comparator.comparing((Entry e) -> e.item.priority(), Comparator.reverseOrder())
                    .thenComparing(time).thenComparing(manual);
        };
    }

    private static List<TaskItem> items(List<Entry> entries, Comparator<Entry> order) {
        return entries.stream().sorted(order).map(Entry::item).toList();
    }

    private static Map<UUID, Map<LocalDate, TaskOccurrence>> rowsByTask(List<TaskOccurrence> rows) {
        Map<UUID, Map<LocalDate, TaskOccurrence>> result = new HashMap<>();
        for (TaskOccurrence row : rows) {
            if (row.getDate() != null) result.computeIfAbsent(row.getTaskId(), k -> new HashMap<>()).put(row.getDate(), row);
        }
        return result;
    }

    private static List<UUID> ids(List<Task> all) {
        return all.stream().map(Task::getId).toList();
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
