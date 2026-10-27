package com.lifepedia.api.task;

import com.lifepedia.api.achievement.Achievement;
import com.lifepedia.api.achievement.AchievementProgress;
import com.lifepedia.api.finance.recurring.RecurringEntryRepository;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRemovedEvent;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.task.TaskDtos.AssignRequest;
import com.lifepedia.api.task.TaskDtos.CheckRequest;
import com.lifepedia.api.task.TaskDtos.MoveRequest;
import com.lifepedia.api.task.TaskDtos.OccurrenceRequest;
import com.lifepedia.api.task.TaskDtos.RepeatRule;
import com.lifepedia.api.task.TaskDtos.Scope;
import com.lifepedia.api.task.TaskDtos.StatusRequest;
import com.lifepedia.api.task.TaskDtos.Subtask;
import com.lifepedia.api.task.TaskDtos.SubtaskInput;
import com.lifepedia.api.task.TaskDtos.TaskRequest;
import com.lifepedia.api.task.TaskDtos.TaskResponse;
import com.lifepedia.api.task.TaskOccurrence.Status;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cadastro e ações das tarefas (concluir, pular, adiar, editar só esta/esta e as próximas/todas). Em grupo, qualquer
 * membro cria, edita e conclui; excluir é do criador ou de um administrador.
 */
@Service
public class TaskService {

    static final int MAX_OPEN = 2000;
    /** Atrasadas de tarefa repetida: só as dos últimos N dias. */
    static final int OVERDUE_DAYS = 90;
    private static final int MAX_YEARS_AHEAD = 20;

    private final TaskRepository tasks;
    private final TaskOccurrenceRepository occurrences;
    private final TaskSubtaskRepository subtasks;
    private final TaskLabelService labels;
    private final GroupMemberRepository members;
    private final TaskCommentRepository comments;
    private final TaskNotifier notifier;
    private final RecurringEntryRepository recurring;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public TaskService(TaskRepository tasks, TaskOccurrenceRepository occurrences, TaskSubtaskRepository subtasks,
                       TaskLabelService labels, GroupMemberRepository members, TaskCommentRepository comments,
                       TaskNotifier notifier, RecurringEntryRepository recurring, Clock clock,
                       ApplicationEventPublisher events) {
        this.tasks = tasks;
        this.occurrences = occurrences;
        this.subtasks = subtasks;
        this.labels = labels;
        this.members = members;
        this.comments = comments;
        this.notifier = notifier;
        this.recurring = recurring;
        this.clock = clock;
        this.events = events;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    @Transactional
    public TaskResponse create(TaskScope scope, TaskRequest request) {
        TaskOwner owner = scope.owner();
        if (tasks.countOpen(owner.userId(), owner.groupId()) >= MAX_OPEN) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_OPEN + " tarefas abertas.");
        }
        Task task = new Task(owner, scope.userId(), tasks.maxPosition(owner.userId(), owner.groupId()) + 1);
        apply(owner, task, request);
        tasks.save(task);
        saveSubtasks(task, request.subtasks());
        notifyAssignment(task, null, scope.userId());
        return response(task, scope);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(TaskScope scope, UUID id) {
        return response(find(scope.owner(), id), scope);
    }

    /** Edita todas as ocorrências ou, com {@code from}, esta e as próximas (a série é dividida no dia). */
    @Transactional
    public TaskResponse update(TaskScope scope, UUID id, TaskRequest request, LocalDate from) {
        TaskOwner owner = scope.owner();
        Task task = find(owner, id);
        Recurrence rule = task.rule();
        Slot before = upcoming(task);
        if (from != null && rule != null && !rule.fromCompletion() && from.isAfter(task.getDueDate())) {
            return split(scope, task, rule, from, request, before);
        }
        LocalDate previousDate = task.getDueDate();
        boolean wasSingle = task.isSingleSlot();
        apply(owner, task, request);
        if (wasSingle && task.isSingleSlot() && !Objects.equals(previousDate, task.getDueDate())) {
            moveSlot(task, previousDate, task.getDueDate());
        }
        syncCompletion(task);
        saveSubtasks(task, request.subtasks());
        notifyAssignment(task, before, scope.userId());
        return response(task, scope);
    }

    /** Edição só desta ocorrência (título, notas, horário, prioridade e dia). */
    @Transactional
    public void updateOccurrence(TaskScope scope, UUID id, OccurrenceRequest request) {
        Task task = find(scope.owner(), id);
        requireSlot(task, request.date());
        String title = request.title().trim();
        String notes = blankToNull(request.notes());
        Priority priority = request.priority() == null ? Priority.NONE : request.priority();
        if (task.getRepeatUnit() == null) {
            task.setDetails(title, notes, priority, task.getListId(), task.isHabit());
            task.setSchedule(task.getDueDate(), request.dueTime(), request.dueTime() == null ? null : task.getDurationMinutes());
            move(task, request.date(), request.moveTo());
            return;
        }
        TaskOccurrence row = row(task, request.date());
        row.override(differs(title, task.getTitle()), differs(notes, task.getNotes()),
                differs(request.dueTime(), task.getDueTime()), differs(priority, task.getPriority()));
        save(row);
        move(task, request.date(), request.moveTo());
    }

    /** Adia só esta ocorrência (sem repetição: muda o dia da tarefa; {@code to} nulo = sem data). */
    @Transactional
    public void move(TaskScope scope, UUID id, MoveRequest request) {
        Task task = find(scope.owner(), id);
        requireSlot(task, request.date());
        move(task, request.date(), request.to());
    }

    /** Responsável só desta ocorrência (sem repetição: o da tarefa). */
    @Transactional
    public void assign(TaskScope scope, UUID id, AssignRequest request) {
        Task task = find(scope.owner(), id);
        if (!scope.isGroup()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Responsável só em tarefas de grupo.");
        requireSlot(task, request.date());
        if (request.assigneeId() != null) requireMembers(task.getGroupId(), List.of(request.assigneeId()));
        UUID previous = assignee(task, request.date(), occurrences.find(task.getId(), request.date()).orElse(null));
        if (task.getRepeatUnit() == null) {
            task.setAssignment(request.assigneeId(), List.of());
        } else {
            TaskOccurrence row = row(task, request.date());
            boolean standard = Objects.equals(request.assigneeId(), assignee(task, request.date(), null));
            row.setAssigneeId(standard ? null : request.assigneeId());
            save(row);
        }
        UUID now = request.assigneeId() != null ? request.assigneeId() : assignee(task, request.date(), null);
        if (now != null && !now.equals(previous)) notifier.assigned(task, request.date(), now, scope.userId());
    }

    @Transactional
    public void delete(TaskScope access, UUID id, Scope scope, LocalDate date) {
        Task task = find(access.owner(), id);
        if (!access.canDelete(task)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Só quem criou a tarefa ou um administrador pode excluí-la.");
        }
        Recurrence rule = task.rule();
        if (rule == null || scope == Scope.ALL) {
            tasks.delete(task);
            return;
        }
        if (date == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Informe a ocorrência.");
        requireSlot(task, date);
        if (scope == Scope.THIS) {
            resolve(task, date, Status.SKIPPED, null);
        } else if (rule.fromCompletion() || !date.isAfter(task.getDueDate())) {
            tasks.delete(task);
        } else {
            endBefore(task, rule, date);
            occurrences.deleteAll(occurrences.from(task.getId(), date));
        }
    }

    /** Conclui, pula ou volta para pendente uma ocorrência. */
    @Transactional
    public void setStatus(TaskScope scope, UUID id, StatusRequest request) {
        Task task = find(scope.owner(), id);
        if (request.status() == Status.SKIPPED && task.getRepeatUnit() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Só tarefas repetidas podem ser puladas.");
        }
        if (request.status() == Status.PENDING) {
            reopen(task, request.date());
            return;
        }
        requireSlot(task, request.date());
        Done done = resolve(task, request.date(), request.status(), scope.userId());
        afterResolve(task, List.of(done), scope.userId());
    }

    /** Conclui ou pula todas as atrasadas da tarefa; devolve quantas mudaram. */
    @Transactional
    public int resolveOverdue(TaskScope scope, UUID id, Status status) {
        if (status == Status.PENDING) throw new BusinessException(HttpStatus.BAD_REQUEST, "Status inválido.");
        UUID userId = scope.userId();
        Task task = find(scope.owner(), id);
        if (status == Status.SKIPPED && task.getRepeatUnit() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Só tarefas repetidas podem ser puladas.");
        }
        LocalDate today = today();
        if (task.isSingleSlot()) {
            if (task.getDueDate() == null || task.getCompletedAt() != null || !task.getDueDate().isBefore(today)) return 0;
            afterResolve(task, List.of(resolve(task, task.getDueDate(), status, userId)), userId);
            return 1;
        }
        LocalDate lower = today.minusDays(OVERDUE_DAYS);
        Map<LocalDate, TaskOccurrence> rows = occurrences.window(List.of(task.getId()), lower, today).stream()
                .filter(o -> o.getDate() != null)
                .collect(Collectors.toMap(TaskOccurrence::getDate, Function.identity(), (a, b) -> a));
        List<Done> resolved = new ArrayList<>();
        for (Recurrence.Occurrence occurrence : task.rule().between(lower, today.minusDays(1))) {
            TaskOccurrence row = rows.get(occurrence.date());
            boolean pending = row == null || row.getStatus() == Status.PENDING;
            LocalDate shown = row != null && row.getMovedTo() != null ? row.getMovedTo() : occurrence.date();
            if (pending && shown.isBefore(today)) resolved.add(resolve(task, occurrence.date(), status, userId));
        }
        if (!resolved.isEmpty()) afterResolve(task, resolved, userId);
        return resolved.size();
    }

    /** Marca/desmarca uma subtarefa numa ocorrência. */
    @Transactional
    public void check(TaskScope scope, UUID id, UUID subtaskId, CheckRequest request) {
        Task task = find(scope.owner(), id);
        subtasks.findById(subtaskId).filter(s -> s.getTaskId().equals(task.getId()))
                .orElseThrow(() -> new NotFoundException("Subtarefa não encontrada."));
        requireSlot(task, request.date());
        TaskOccurrence row = row(task, request.date());
        if (request.done()) row.getCheckedSubtasks().add(subtaskId);
        else row.getCheckedSubtasks().remove(subtaskId);
        save(row);
    }

    /** Nova ordem manual: as tarefas listadas trocam de posição entre si, na ordem dada. */
    @Transactional
    public void reorder(TaskScope scope, List<UUID> orderedIds) {
        List<UUID> ids = orderedIds.stream().distinct().toList();
        Map<UUID, Task> byId = tasks.findAllById(ids).stream()
                .filter(t -> t.ownedBy(scope.owner())).collect(Collectors.toMap(Task::getId, Function.identity()));
        if (byId.size() != ids.size()) throw new NotFoundException("Tarefa não encontrada.");
        List<Integer> positions = byId.values().stream().map(Task::getPosition).sorted().toList();
        int previous = Integer.MIN_VALUE;
        for (int i = 0; i < ids.size(); i++) {
            int position = Math.max(positions.get(i), previous + 1);
            byId.get(ids.get(i)).setPosition(position);
            previous = position;
        }
    }

    /** Quem sai do grupo deixa de ser responsável e sai dos rodízios; os comentários dele saem junto. */
    @EventListener
    public void onMemberRemoved(GroupMemberRemovedEvent event) {
        tasks.involving(event.groupId(), event.userId()).forEach(t -> t.removeMember(event.userId()));
        tasks.flush();
        occurrences.clearAssignee(event.groupId(), event.userId());
        comments.deleteByGroupAndUser(event.groupId(), event.userId());
    }

    Task find(TaskOwner owner, UUID id) {
        return tasks.findById(id).filter(t -> t.ownedBy(owner))
                .orElseThrow(() -> new NotFoundException("Tarefa não encontrada."));
    }

    /** Responsável da ocorrência: trocado só nela, vez do rodízio ou o fixo. */
    static UUID assignee(Task task, LocalDate date, TaskOccurrence row) {
        if (row != null && row.getAssigneeId() != null) return row.getAssigneeId();
        if (task.getRotation().isEmpty() || task.isSingleSlot()) return task.assigneeAt(0);
        return task.assigneeAt(Math.max(task.rule().indexOf(date), 0));
    }

    private void apply(TaskOwner owner, Task task, TaskRequest request) {
        labels.requireList(owner, request.listId());
        Set<UUID> tagIds = request.tagIds() == null ? Set.of() : request.tagIds();
        labels.requireTags(owner, tagIds);
        if (request.dueTime() != null && request.dueDate() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha o dia para definir o horário.");
        }
        if (request.durationMinutes() != null && request.dueTime() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Defina o horário para informar a duração.");
        }
        if (request.dueDate() != null && request.dueDate().isAfter(today().plusYears(MAX_YEARS_AHEAD))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Data muito distante.");
        }
        if (request.reminderMinutes() != null && request.dueTime() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Defina o horário para ter lembrete.");
        }
        Recurrence rule = rule(request.repeat(), request.dueDate());
        boolean habit = Boolean.TRUE.equals(request.habit());
        if (habit && (rule == null || rule.fromCompletion())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Hábito precisa de repetição em dias fixos.");
        }
        requireRecurring(owner, request.recurringEntryId());
        task.setDetails(request.title().trim(), blankToNull(request.notes()),
                request.priority() == null ? Priority.NONE : request.priority(), request.listId(), habit);
        task.setSchedule(request.dueDate(), request.dueTime(), request.durationMinutes());
        task.setReminder(request.reminderMinutes());
        task.setRecurringEntryId(request.recurringEntryId());
        task.setRepeat(rule);
        task.setTagIds(tagIds);
        assignment(owner, task, request, rule);
    }

    /** Fixo do financeiro: só em tarefa pessoal e da própria pessoa. */
    private void requireRecurring(TaskOwner owner, UUID recurringEntryId) {
        if (recurringEntryId == null) return;
        if (owner.userId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Fixo do financeiro só em tarefas pessoais.");
        }
        if (recurring.findByIdAndUserId(recurringEntryId, owner.userId()).isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Fixo não encontrado.");
        }
    }

    private void assignment(TaskOwner owner, Task task, TaskRequest request, Recurrence rule) {
        List<UUID> rotation = request.rotation() == null ? List.of() : request.rotation();
        if (owner.groupId() == null) {
            if (request.assigneeId() != null || !rotation.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Responsável só em tarefas de grupo.");
            }
            return;
        }
        if (!rotation.isEmpty()) {
            if (request.assigneeId() != null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha um responsável fixo ou o rodízio, não os dois.");
            }
            if (rule == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Rodízio precisa de repetição.");
            if (rotation.size() < 2 || new HashSet<>(rotation).size() != rotation.size()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "O rodízio precisa de pelo menos 2 pessoas, sem repetir.");
            }
        }
        List<UUID> people = new ArrayList<>(rotation);
        if (request.assigneeId() != null) people.add(request.assigneeId());
        requireMembers(owner.groupId(), people);
        task.setAssignment(request.assigneeId(), rotation);
    }

    private void requireMembers(UUID groupId, List<UUID> people) {
        if (people.isEmpty()) return;
        Set<UUID> ids = members.findByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .map(GroupMember::getUserId).collect(Collectors.toSet());
        if (!ids.containsAll(people)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Responsável precisa ser membro do grupo.");
        }
    }

    static Recurrence rule(RepeatRule repeat, LocalDate start) {
        if (repeat == null) return null;
        if (start == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha o dia de início da repetição.");
        if (repeat.until() != null && repeat.count() != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha terminar em uma data ou após N vezes, não os dois.");
        }
        if (repeat.until() != null && repeat.until().isBefore(start)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O fim da repetição deve ser depois do início.");
        }
        boolean fromCompletion = Boolean.TRUE.equals(repeat.fromCompletion());
        int weekdays = 0;
        if (repeat.unit() == Recurrence.Unit.WEEK && !fromCompletion && repeat.weekdays() != null) {
            for (int day : repeat.weekdays()) weekdays |= Recurrence.bit(DayOfWeek.of(day));
        }
        Recurrence.MonthMode monthMode = repeat.unit() == Recurrence.Unit.MONTH && !fromCompletion ? repeat.monthMode() : null;
        return new Recurrence(start, repeat.unit(), repeat.interval(), weekdays, monthMode, fromCompletion,
                repeat.until(), repeat.count());
    }

    /** Divide a série: a atual acaba antes de {@code from} e uma nova começa com os dados pedidos. */
    private TaskResponse split(TaskScope scope, Task task, Recurrence rule, LocalDate from, TaskRequest request, Slot before) {
        TaskOwner owner = scope.owner();
        requireSlot(task, from);
        Task next = new Task(owner, task.getCreatedBy(), task.getPosition());
        apply(owner, next, request);
        // O rodízio continua de onde a série antiga parou.
        next.setRotationStart(task.getRotationStart() + rule.countBefore(from));
        tasks.save(next);
        endBefore(task, rule, from);
        Recurrence nextRule = next.rule();
        for (TaskOccurrence row : occurrences.from(task.getId(), from)) {
            if (nextRule != null && !nextRule.fromCompletion() && nextRule.occursOn(row.getDate())) row.moveTo(next.getId());
            else occurrences.delete(row);
        }
        occurrences.flush();
        syncCompletion(next);
        saveSubtasks(next, request.subtasks() == null ? null
                : request.subtasks().stream().map(s -> new SubtaskInput(null, s.title())).toList());
        notifyAssignment(next, before, scope.userId());
        return response(next, scope);
    }

    private static void endBefore(Task task, Recurrence rule, LocalDate date) {
        if (rule.count() != null) task.endRepeat(null, rule.countBefore(date));
        else task.endRepeat(date.minusDays(1), null);
    }

    /** Ocorrência válida da tarefa? (sem repetição: o dia dela, inclusive nulo). */
    private static void requireSlot(Task task, LocalDate date) {
        boolean valid = task.isSingleSlot()
                ? Objects.equals(date, task.getDueDate())
                : date != null && task.rule().occursOn(date);
        if (!valid) throw new BusinessException(HttpStatus.BAD_REQUEST, "Ocorrência inválida.");
    }

    /** Ocorrência resolvida: dia, título mostrado, status e quem responde por ela (responsável ou criador). */
    private record Done(LocalDate date, String title, Status status, UUID responsible) {
    }

    private Done resolve(Task task, LocalDate date, Status status, UUID userId) {
        TaskOccurrence row = row(task, date);
        UUID assignee = task.getGroupId() == null ? null : assignee(task, date, row);
        // Guarda quem era o responsável (o rodízio pode mudar depois).
        if (assignee != null) row.setAssigneeId(assignee);
        if (status == Status.DONE) {
            row.complete(OffsetDateTime.now(clock), userId);
            events.publishEvent(new AchievementProgress(userId, task.getGroupId() != null
                    ? EnumSet.of(Achievement.TASKS_DONE, Achievement.GROUP_TASKS_DONE)
                    : task.isHabit() ? EnumSet.of(Achievement.TASKS_DONE, Achievement.HABIT_STREAK)
                    : EnumSet.of(Achievement.TASKS_DONE)));
        } else {
            row.setStatus(status);
        }
        occurrences.save(row);
        if (task.getRepeatUnit() == null) {
            task.setCompletedAt(status == Status.DONE ? row.getCompletedAt() : null);
        } else if (task.isSingleSlot()) {
            advance(task);
        }
        return new Done(date, row.getTitle() != null ? row.getTitle() : task.getTitle(), status,
                assignee != null ? assignee : task.getCreatedBy());
    }

    /** Avisa o responsável quando outra pessoa concluiu e, no rodízio, de quem é a próxima vez. */
    private void afterResolve(Task task, List<Done> done, UUID actorId) {
        if (task.getGroupId() == null) return;
        Set<UUID> told = new LinkedHashSet<>();
        for (Done d : done.reversed()) {
            if (d.status() == Status.DONE && d.responsible() != null && told.add(d.responsible())) {
                notifier.completed(task, d.date(), d.title(), d.responsible(), actorId);
            }
        }
        if (task.getRotation().isEmpty()) return;
        Slot next = nextAfter(task, done.getLast().date());
        if (next != null) notifier.turn(task, next.date(), next.assignee(), actorId);
    }

    /** Uma ocorrência e o responsável dela. */
    private record Slot(LocalDate date, UUID assignee) {
    }

    /** Próxima ocorrência pendente, de hoje em diante (sem repetição/sem data: a da tarefa); nula se não houver. */
    private Slot upcoming(Task task) {
        if (task.getGroupId() == null || task.getCompletedAt() != null) return null;
        if (task.isSingleSlot()) {
            return new Slot(task.getDueDate(), assignee(task, task.getDueDate(),
                    occurrences.find(task.getId(), task.getDueDate()).orElse(null)));
        }
        return nextAfter(task, today().minusDays(1));
    }

    /** Primeira ocorrência depois de {@code date} que ainda não passou. */
    private Slot nextAfter(Task task, LocalDate date) {
        if (task.getCompletedAt() != null) return null;
        if (task.isSingleSlot()) return upcoming(task);
        LocalDate from = date.isBefore(today()) ? today() : date.plusDays(1);
        List<Recurrence.Occurrence> next = task.rule().between(from, from.plusYears(1));
        if (next.isEmpty()) return null;
        LocalDate day = next.getFirst().date();
        return new Slot(day, assignee(task, day, occurrences.find(task.getId(), day).orElse(null)));
    }

    /** Avisa quem passou a responder pela próxima ocorrência. */
    private void notifyAssignment(Task task, Slot before, UUID actorId) {
        Slot now = upcoming(task);
        if (now == null || now.assignee() == null) return;
        if (before != null && now.assignee().equals(before.assignee())) return;
        if (task.getRotation().isEmpty()) notifier.assigned(task, now.date(), now.assignee(), actorId);
        else notifier.turn(task, now.date(), now.assignee(), actorId);
    }

    /** "A partir da conclusão": a próxima conta de hoje (ou do dia dela, se resolvida antes da hora). */
    private void advance(Task task) {
        Recurrence rule = task.rule();
        LocalDate today = today();
        LocalDate next = rule.after(task.getDueDate().isBefore(today) ? today : task.getDueDate());
        int resolved = occurrences.resolved(task.getId(), List.of(Status.DONE, Status.SKIPPED)).size();
        if (!rule.allows(next, resolved)) {
            task.setCompletedAt(OffsetDateTime.now(clock));
            return;
        }
        occurrences.find(task.getId(), next).ifPresent(occurrences::delete);
        occurrences.flush();
        task.setDueDate(next);
        task.turn(1);
    }

    /** Volta para pendente. "A partir da conclusão": só a última resolvida, e a série volta para ela. */
    private void reopen(Task task, LocalDate date) {
        TaskOccurrence row = occurrences.find(task.getId(), date)
                .filter(o -> o.getStatus() != Status.PENDING)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "Ocorrência inválida."));
        if (task.getRepeatUnit() != null && task.isSingleSlot()) {
            TaskOccurrence last = occurrences.resolved(task.getId(), List.of(Status.DONE, Status.SKIPPED)).getFirst();
            if (!last.getId().equals(row.getId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Só dá para desfazer a última.");
            }
            occurrences.find(task.getId(), task.getDueDate())
                    .filter(o -> !o.getId().equals(row.getId()) && o.isPlain()).ifPresent(occurrences::delete);
            if (task.getCompletedAt() == null) task.turn(-1);
            task.setDueDate(date);
        }
        // Sem repetição o responsável fica na tarefa; o guardado ao concluir sai.
        if (task.getRepeatUnit() == null) row.setAssigneeId(null);
        row.setStatus(Status.PENDING);
        task.setCompletedAt(null);
        save(row);
    }

    private void move(Task task, LocalDate date, LocalDate to) {
        if (Objects.equals(date, to)) {
            if (!task.isSingleSlot()) occurrences.find(task.getId(), date).ifPresent(o -> {
                o.setMovedTo(null);
                save(o);
            });
            return;
        }
        if (to != null && to.isAfter(today().plusYears(MAX_YEARS_AHEAD))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Data muito distante.");
        }
        if (task.isSingleSlot()) {
            if (to == null && task.getRepeatUnit() != null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Tarefa repetida precisa de dia.");
            }
            if (to == null) task.setSchedule(null, null, null);
            else task.setDueDate(to);
            moveSlot(task, date, to);
            return;
        }
        if (to == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Informe o novo dia.");
        TaskOccurrence row = row(task, date);
        row.setMovedTo(to);
        save(row);
    }

    /** Leva o registro da ocorrência única para o novo dia (descarta registro antigo que já estivesse lá). */
    private void moveSlot(Task task, LocalDate from, LocalDate to) {
        occurrences.find(task.getId(), to).ifPresent(occurrences::delete);
        occurrences.flush();
        occurrences.find(task.getId(), from).ifPresent(o -> o.setDate(to));
    }

    /** Tarefa sem repetição fica concluída se o registro do dia dela estiver concluído. */
    private void syncCompletion(Task task) {
        if (task.getRepeatUnit() != null) {
            if (!task.isSingleSlot()) task.setCompletedAt(null);
            return;
        }
        task.setCompletedAt(occurrences.find(task.getId(), task.getDueDate())
                .filter(o -> o.getStatus() == Status.DONE).map(TaskOccurrence::getCompletedAt).orElse(null));
    }

    private TaskOccurrence row(Task task, LocalDate date) {
        return occurrences.find(task.getId(), date).orElseGet(() -> new TaskOccurrence(task.getId(), date));
    }

    /** Grava o registro ou o apaga se não diferir mais da tarefa. */
    private void save(TaskOccurrence row) {
        if (!row.isPlain()) occurrences.save(row);
        else if (row.getId() != null) occurrences.delete(row);
    }

    /** Sincroniza o checklist: mantém os ids enviados, cria os novos e apaga os que saíram. */
    private void saveSubtasks(Task task, List<SubtaskInput> input) {
        List<SubtaskInput> wanted = input == null ? List.of() : input;
        Map<UUID, TaskSubtask> current = subtasks.findByTaskIdOrderByPositionAsc(task.getId()).stream()
                .collect(Collectors.toMap(TaskSubtask::getId, Function.identity()));
        Set<UUID> kept = new HashSet<>();
        List<TaskSubtask> toSave = new ArrayList<>();
        for (int i = 0; i < wanted.size(); i++) {
            SubtaskInput item = wanted.get(i);
            TaskSubtask subtask = item.id() == null ? null : current.get(item.id());
            if (item.id() != null && subtask == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Subtarefa inválida.");
            }
            if (subtask == null) subtask = new TaskSubtask(task.getId(), item.title().trim(), i);
            else subtask.set(item.title().trim(), i);
            kept.add(subtask.getId());
            toSave.add(subtask);
        }
        subtasks.deleteAll(current.values().stream().filter(s -> !kept.contains(s.getId())).toList());
        subtasks.saveAll(toSave);
    }

    private TaskResponse response(Task task, TaskScope scope) {
        List<Subtask> items = subtasks.findByTaskIdOrderByPositionAsc(task.getId()).stream()
                .sorted(Comparator.comparingInt(TaskSubtask::getPosition))
                .map(s -> new Subtask(s.getId(), s.getTitle())).toList();
        return new TaskResponse(task.getId(), task.getTitle(), task.getNotes(), task.getPriority(), task.getDueDate(),
                task.getDueTime(), task.getDurationMinutes(), task.getListId(), Set.copyOf(task.getTagIds()),
                task.isHabit(), repeat(task.rule()), items, task.getCompletedAt(), task.getGroupId(),
                task.getCreatedBy(), task.getAssigneeId(), task.getRotation(), scope.canDelete(task),
                task.getReminderMinutes(), task.getRecurringEntryId());
    }

    static RepeatRule repeat(Recurrence rule) {
        if (rule == null) return null;
        List<Integer> weekdays = new ArrayList<>();
        if (rule.unit() == Recurrence.Unit.WEEK) {
            for (DayOfWeek day : DayOfWeek.values()) {
                if ((rule.weekdays() & Recurrence.bit(day)) != 0) weekdays.add(day.getValue());
            }
        }
        return new RepeatRule(rule.unit(), rule.interval(), weekdays, rule.monthMode(), rule.fromCompletion(),
                rule.until(), rule.count());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T differs(T value, T original) {
        return Objects.equals(value, original) ? null : value;
    }
}
