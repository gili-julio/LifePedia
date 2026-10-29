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
import com.lifepedia.api.task.TaskDtos.GroupMoveRequest;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
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

    /** Fuso do app ({@code TIME_ZONE}). */
    public ZoneId zone() {
        return clock.getZone();
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

    /** Uma pessoa só nesta ocorrência (sem repetição: vira a responsável da tarefa; nulo = ninguém/volta ao padrão). */
    @Transactional
    public void assign(TaskScope scope, UUID id, AssignRequest request) {
        Task task = find(scope.owner(), id);
        if (!scope.isGroup()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Responsável só em tarefas de grupo.");
        requireSlot(task, request.date());
        if (request.assigneeId() != null) requireMembers(task.getGroupId(), List.of(request.assigneeId()));
        List<UUID> everyone = members(task);
        List<UUID> previous = assignees(task, request.date(), occurrences.find(task.getId(), request.date()).orElse(null), everyone);
        UUID now = request.assigneeId();
        if (task.getRepeatUnit() == null) {
            task.setAssignment(now == null ? Set.of() : Set.of(now), false, List.of(), Completion.ANY);
        } else {
            TaskOccurrence row = row(task, request.date());
            boolean standard = now == null || List.of(now).equals(assignees(task, request.date(), null, everyone));
            row.setAssigneeId(standard ? null : now);
            save(row);
        }
        if (now != null && !previous.equals(List.of(now))) notifier.assigned(task, request.date(), now, scope.userId());
    }

    /** Leva a tarefa pessoal para um grupo de que a pessoa é membro; ela fica como responsável. */
    @Transactional
    public TaskResponse moveToGroup(TaskScope scope, UUID id, GroupMoveRequest request) {
        if (scope.isGroup()) throw new BusinessException(HttpStatus.BAD_REQUEST, "A tarefa já é de um grupo.");
        Task task = find(scope.owner(), id);
        UUID groupId = request.groupId();
        if (!members.existsByGroupIdAndUserId(groupId, scope.userId())) throw new NotFoundException("Grupo não encontrado.");
        if (tasks.countOpen(null, groupId) >= MAX_OPEN) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O grupo chegou ao limite de " + MAX_OPEN + " tarefas abertas.");
        }
        task.moveToGroup(groupId, tasks.maxPosition(null, groupId) + 1, scope.userId());
        return response(task, new TaskScope(TaskOwner.group(groupId), scope.userId(), false));
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
            resolve(task, row(task, date), Status.SKIPPED, null);
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
            reopen(task, request.date(), scope.userId());
            return;
        }
        requireSlot(task, request.date());
        Done done = request.status() == Status.DONE
                ? complete(task, request.date(), scope.userId())
                : resolve(task, row(task, request.date()), request.status(), scope.userId());
        if (done != null) afterResolve(task, List.of(done), scope.userId());
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
            Done done = status == Status.DONE ? complete(task, task.getDueDate(), userId)
                    : resolve(task, row(task, task.getDueDate()), status, userId);
            if (done != null) afterResolve(task, List.of(done), userId);
            return 1;
        }
        LocalDate lower = today.minusDays(OVERDUE_DAYS);
        Map<LocalDate, TaskOccurrence> rows = occurrences.window(List.of(task.getId()), lower, today).stream()
                .filter(o -> o.getDate() != null)
                .collect(Collectors.toMap(TaskOccurrence::getDate, Function.identity(), (a, b) -> a));
        List<Done> resolved = new ArrayList<>();
        int changed = 0;
        for (Recurrence.Occurrence occurrence : task.rule().between(lower, today.minusDays(1))) {
            TaskOccurrence row = rows.get(occurrence.date());
            boolean pending = row == null || row.getStatus() == Status.PENDING;
            LocalDate shown = row != null && row.getMovedTo() != null ? row.getMovedTo() : occurrence.date();
            if (!pending || !shown.isBefore(today)) continue;
            Done done = status == Status.DONE ? complete(task, occurrence.date(), userId)
                    : resolve(task, row(task, occurrence.date()), status, userId);
            if (done != null) resolved.add(done);
            changed++;
        }
        if (!resolved.isEmpty()) afterResolve(task, resolved, userId);
        return changed;
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

    /** Responsáveis da ocorrência: trocado só nela, vez do rodízio, todos do grupo ({@code everyone}) ou os fixos. */
    static List<UUID> assignees(Task task, LocalDate date, TaskOccurrence row, List<UUID> everyone) {
        if (row != null && row.getAssigneeId() != null) return List.of(row.getAssigneeId());
        if (!task.getRotation().isEmpty()) {
            return List.of(task.turnAt(task.isSingleSlot() ? 0 : Math.max(task.rule().indexOf(date), 0)));
        }
        if (task.isAllMembers()) return everyone;
        return task.getAssignees().stream().sorted().toList();
    }

    /** Membros do grupo, na ordem de entrada (só para tarefa de "todos"; nas outras, vazio). */
    List<UUID> members(Task task) {
        if (task.getGroupId() == null || !task.isAllMembers()) return List.of();
        return members.findByGroupIdOrderByCreatedAtAsc(task.getGroupId()).stream().map(GroupMember::getUserId).toList();
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
        Set<UUID> fixed = request.assignees() == null ? Set.of() : new LinkedHashSet<>(request.assignees());
        boolean all = Boolean.TRUE.equals(request.allMembers());
        if (owner.groupId() == null) {
            if (!fixed.isEmpty() || all || !rotation.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Responsável só em tarefas de grupo.");
            }
            return;
        }
        if (all && !fixed.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha \"todos do grupo\" ou as pessoas, não os dois.");
        }
        if (!rotation.isEmpty()) {
            if (!fixed.isEmpty() || all) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha os responsáveis ou o rodízio, não os dois.");
            }
            if (rule == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Rodízio precisa de repetição.");
            if (rotation.size() < 2 || new HashSet<>(rotation).size() != rotation.size()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "O rodízio precisa de pelo menos 2 pessoas, sem repetir.");
            }
        }
        List<UUID> people = new ArrayList<>(rotation);
        people.addAll(fixed);
        requireMembers(owner.groupId(), people);
        task.setAssignment(fixed, all, rotation, request.completion());
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

    /** Ocorrência resolvida: dia, título mostrado, status e a quem avisar (responsáveis que não agiram, ou o criador). */
    private record Done(LocalDate date, String title, Status status, List<UUID> responsible) {
    }

    /** "Cada um conclui a sua": o responsável marca a parte dele e a ocorrência fecha quando todos marcarem (nulo até lá). */
    private Done complete(Task task, LocalDate date, UUID userId) {
        TaskOccurrence row = row(task, date);
        if (task.getCompletion() != Completion.EACH || row.getStatus() == Status.DONE) {
            return resolve(task, row, Status.DONE, userId);
        }
        List<UUID> people = assignees(task, date, row, members(task));
        if (!people.contains(userId)) return resolve(task, row, Status.DONE, userId);
        row.getDoneBy().add(userId);
        if (row.getDoneBy().containsAll(people)) return resolve(task, row, Status.DONE, userId);
        occurrences.save(row);
        return null;
    }

    private Done resolve(Task task, TaskOccurrence row, Status status, UUID userId) {
        LocalDate date = row.getDate();
        List<UUID> people = task.getGroupId() == null ? List.of() : assignees(task, date, row, members(task));
        // Guarda de quem era a vez (o rodízio pode mudar depois).
        if (!task.getRotation().isEmpty() && !people.isEmpty()) row.setAssigneeId(people.getFirst());
        List<UUID> responsible = people.isEmpty() ? Collections.singletonList(task.getCreatedBy())
                : people.stream().filter(p -> !row.getDoneBy().contains(p)).toList();
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
        return new Done(date, row.getTitle() != null ? row.getTitle() : task.getTitle(), status, responsible);
    }

    /** Avisa o responsável quando outra pessoa concluiu e, no rodízio, de quem é a próxima vez. */
    private void afterResolve(Task task, List<Done> done, UUID actorId) {
        if (task.getGroupId() == null) return;
        Set<UUID> told = new LinkedHashSet<>();
        for (Done d : done.reversed()) {
            if (d.status() != Status.DONE) continue;
            for (UUID to : d.responsible()) {
                if (to != null && told.add(to)) notifier.completed(task, d.date(), d.title(), to, actorId);
            }
        }
        if (task.getRotation().isEmpty()) return;
        Slot next = nextAfter(task, done.getLast().date());
        if (next != null && !next.assignees().isEmpty()) notifier.turn(task, next.date(), next.assignees().getFirst(), actorId);
    }

    /** Uma ocorrência e os responsáveis dela. */
    private record Slot(LocalDate date, List<UUID> assignees) {
    }

    /** Próxima ocorrência pendente, de hoje em diante (sem repetição/sem data: a da tarefa); nula se não houver. */
    private Slot upcoming(Task task) {
        if (task.getGroupId() == null || task.getCompletedAt() != null) return null;
        if (task.isSingleSlot()) {
            return new Slot(task.getDueDate(), assignees(task, task.getDueDate(),
                    occurrences.find(task.getId(), task.getDueDate()).orElse(null), members(task)));
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
        return new Slot(day, assignees(task, day, occurrences.find(task.getId(), day).orElse(null), members(task)));
    }

    /** Avisa quem passou a responder pela próxima ocorrência ("todos do grupo" não avisa ninguém). */
    private void notifyAssignment(Task task, Slot before, UUID actorId) {
        Slot now = upcoming(task);
        if (now == null || task.isAllMembers()) return;
        Set<UUID> previous = before == null ? Set.of() : Set.copyOf(before.assignees());
        for (UUID person : now.assignees()) {
            if (previous.contains(person)) continue;
            if (task.getRotation().isEmpty()) notifier.assigned(task, now.date(), person, actorId);
            else notifier.turn(task, now.date(), person, actorId);
        }
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

    /**
     * Volta para pendente. "A partir da conclusão": só a última resolvida, e a série volta para ela. "Cada um conclui a
     * sua": desfaz só a parte de quem pediu.
     */
    private void reopen(Task task, LocalDate date, UUID userId) {
        TaskOccurrence partial = occurrences.find(task.getId(), date).orElse(null);
        if (partial != null && partial.getStatus() == Status.PENDING && partial.getDoneBy().remove(userId)) {
            save(partial);
            return;
        }
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
        row.getDoneBy().remove(userId);
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
                task.getCreatedBy(), task.getAssignees().stream().sorted().toList(), task.isAllMembers(),
                task.getRotation(), task.getCompletion(), scope.canDelete(task), task.getReminderMinutes(),
                task.getRecurringEntryId());
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
