package com.lifepedia.api.task;

import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.shared.CurrentUser;
import com.lifepedia.api.shared.PageResponse;
import com.lifepedia.api.task.TaskAgendaService.Filter;
import com.lifepedia.api.task.TaskDtos.Agenda;
import com.lifepedia.api.task.TaskDtos.AssignRequest;
import com.lifepedia.api.task.TaskDtos.CheckRequest;
import com.lifepedia.api.task.TaskDtos.CommentRequest;
import com.lifepedia.api.task.TaskDtos.CommentResponse;
import com.lifepedia.api.task.TaskDtos.Conflict;
import com.lifepedia.api.task.TaskDtos.GroupMoveRequest;
import com.lifepedia.api.task.TaskDtos.HabitSummary;
import com.lifepedia.api.task.TaskDtos.HistoryItem;
import com.lifepedia.api.task.TaskDtos.Label;
import com.lifepedia.api.task.TaskDtos.LabelRequest;
import com.lifepedia.api.task.TaskDtos.Labels;
import com.lifepedia.api.task.TaskDtos.MoveRequest;
import com.lifepedia.api.task.TaskDtos.OccurrenceRequest;
import com.lifepedia.api.task.TaskDtos.OrderRequest;
import com.lifepedia.api.task.TaskDtos.OverdueRequest;
import com.lifepedia.api.task.TaskDtos.OverdueResult;
import com.lifepedia.api.task.TaskDtos.Scope;
import com.lifepedia.api.task.TaskDtos.Sort;
import com.lifepedia.api.task.TaskDtos.StatusRequest;
import com.lifepedia.api.task.TaskDtos.TaskItem;
import com.lifepedia.api.task.TaskDtos.TaskRequest;
import com.lifepedia.api.task.TaskDtos.TaskResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Tarefas pessoais ({@code /api/tasks}) e de grupo ({@code /api/groups/{groupId}/tasks}, só membros). As rotas são as
 * mesmas; responsável, rodízio e comentários só em grupo.
 */
@RestController
@RequestMapping({"/api/tasks", "/api/groups/{groupId}/tasks"})
public class TaskController {

    private final TaskService service;
    private final TaskAgendaService agenda;
    private final TaskLabelService labels;
    private final TaskCommentService comments;
    private final GroupAccess access;

    public TaskController(TaskService service, TaskAgendaService agenda, TaskLabelService labels,
                          TaskCommentService comments, GroupAccess access) {
        this.service = service;
        this.agenda = agenda;
        this.labels = labels;
        this.comments = comments;
        this.access = access;
    }

    /**
     * Dias de {@code from} a {@code to} (até 62); {@code overdue=true} traz as atrasadas antes de {@code from}.
     * {@code assignee}: só as desse responsável (grupo).
     */
    @GetMapping("/agenda")
    public Agenda agenda(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                         @RequestParam LocalDate from, @RequestParam LocalDate to,
                         @RequestParam(defaultValue = "false") boolean overdue,
                         @RequestParam(required = false) UUID list, @RequestParam(required = false) UUID tag,
                         @RequestParam(required = false) UUID assignee, @RequestParam(required = false) Sort sort) {
        return agenda.agenda(scope(jwt, groupId), from, to, overdue, new Filter(list, tag, assignee), sort);
    }

    /**
     * Pendentes de {@code date} no mesmo horário ({@code duration} nula = 30 min). Grupo: {@code people} = responsáveis
     * pensados (vazio = sem responsável). {@code task}: a própria tarefa sai da conta.
     */
    @GetMapping("/conflicts")
    public List<Conflict> conflicts(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                    @RequestParam LocalDate date, @RequestParam LocalTime time,
                                    @RequestParam(required = false) Integer duration,
                                    @RequestParam(required = false) List<UUID> people,
                                    @RequestParam(required = false) UUID task) {
        return agenda.conflicts(scope(jwt, groupId), date, time, duration, people == null ? List.of() : people, task);
    }

    @GetMapping("/undated")
    public List<TaskItem> undated(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                  @RequestParam(required = false) UUID list, @RequestParam(required = false) UUID tag,
                                  @RequestParam(required = false) UUID assignee, @RequestParam(required = false) Sort sort) {
        return agenda.undated(scope(jwt, groupId), new Filter(list, tag, assignee), sort);
    }

    @GetMapping("/history")
    public PageResponse<HistoryItem> history(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String sort) {
        return agenda.history(scope(jwt, groupId), page, size, sort);
    }

    /** Hábitos em andamento com sequência, % do mês e os últimos 7 dias. */
    @GetMapping("/habits")
    public List<HabitSummary> habits(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId) {
        return agenda.habits(scope(jwt, groupId));
    }

    @GetMapping("/{id}")
    public TaskResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                            @PathVariable UUID id) {
        return service.get(scope(jwt, groupId), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                               @Valid @RequestBody TaskRequest request) {
        return service.create(scope(jwt, groupId), request);
    }

    /** Sem {@code from}: todas as ocorrências. Com {@code from}: esta e as próximas (devolve a nova série). */
    @PutMapping("/{id}")
    public TaskResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                               @PathVariable UUID id, @RequestParam(required = false) LocalDate from,
                               @Valid @RequestBody TaskRequest request) {
        return service.update(scope(jwt, groupId), id, request, from);
    }

    @PutMapping("/{id}/occurrence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateOccurrence(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                 @PathVariable UUID id, @Valid @RequestBody OccurrenceRequest request) {
        service.updateOccurrence(scope(jwt, groupId), id, request);
    }

    @PutMapping("/{id}/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void move(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                     @PathVariable UUID id, @Valid @RequestBody MoveRequest request) {
        service.move(scope(jwt, groupId), id, request);
    }

    /** Responsável só desta ocorrência (grupo). */
    @PutMapping("/{id}/assignee")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                       @PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
        service.assign(scope(jwt, groupId), id, request);
    }

    /** Tarefa pessoal vai para um grupo de que a pessoa é membro (ela vira a responsável). */
    @PutMapping("/{id}/group")
    public TaskResponse moveToGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                    @PathVariable UUID id, @Valid @RequestBody GroupMoveRequest request) {
        return service.moveToGroup(scope(jwt, groupId), id, request);
    }

    /** Em grupo: só quem criou ou um administrador. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                       @PathVariable UUID id, @RequestParam(defaultValue = "ALL") Scope scope,
                       @RequestParam(required = false) LocalDate date) {
        service.delete(scope(jwt, groupId), id, scope, date);
    }

    @PutMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void status(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                       @PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        service.setStatus(scope(jwt, groupId), id, request);
    }

    @PutMapping("/{id}/overdue")
    public OverdueResult overdue(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                 @PathVariable UUID id, @Valid @RequestBody OverdueRequest request) {
        return new OverdueResult(service.resolveOverdue(scope(jwt, groupId), id, request.status()));
    }

    @PutMapping("/{id}/subtasks/{subtaskId}/check")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void check(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                      @PathVariable UUID id, @PathVariable UUID subtaskId, @Valid @RequestBody CheckRequest request) {
        service.check(scope(jwt, groupId), id, subtaskId, request);
    }

    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                        @Valid @RequestBody OrderRequest request) {
        service.reorder(scope(jwt, groupId), request.taskIds());
    }

    /** Comentários (grupo), mais recentes primeiro. */
    @GetMapping("/{id}/comments")
    public PageResponse<CommentResponse> comments(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable(required = false) UUID groupId, @PathVariable UUID id,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String sort) {
        return comments.page(scope(jwt, groupId), id, page, size, sort);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse comment(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                   @PathVariable UUID id, @Valid @RequestBody CommentRequest request) {
        return comments.add(scope(jwt, groupId), id, request);
    }

    /** Só quem escreveu ou um administrador. */
    @DeleteMapping("/{id}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                              @PathVariable UUID id, @PathVariable UUID commentId) {
        comments.delete(scope(jwt, groupId), id, commentId);
    }

    @GetMapping("/labels")
    public Labels labels(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId) {
        return labels.labels(owner(jwt, groupId));
    }

    @PostMapping("/lists")
    @ResponseStatus(HttpStatus.CREATED)
    public Label createList(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                            @Valid @RequestBody LabelRequest request) {
        return labels.createList(owner(jwt, groupId), request);
    }

    @PutMapping("/lists/{id}")
    public Label updateList(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                            @PathVariable UUID id, @Valid @RequestBody LabelRequest request) {
        return labels.updateList(owner(jwt, groupId), id, request);
    }

    @DeleteMapping("/lists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteList(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                           @PathVariable UUID id) {
        labels.deleteList(owner(jwt, groupId), id);
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public Label createTag(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                           @Valid @RequestBody LabelRequest request) {
        return labels.createTag(owner(jwt, groupId), request);
    }

    @PutMapping("/tags/{id}")
    public Label updateTag(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                           @PathVariable UUID id, @Valid @RequestBody LabelRequest request) {
        return labels.updateTag(owner(jwt, groupId), id, request);
    }

    @DeleteMapping("/tags/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTag(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                          @PathVariable UUID id) {
        labels.deleteTag(owner(jwt, groupId), id);
    }

    /** Pessoal ou grupo (quem não é membro recebe 404). */
    private TaskScope scope(Jwt jwt, UUID groupId) {
        UUID userId = CurrentUser.id(jwt);
        if (groupId == null) return TaskScope.personal(userId);
        GroupMember member = access.requireMember(groupId, userId);
        return new TaskScope(TaskOwner.group(groupId), userId, member.isAdmin());
    }

    private TaskOwner owner(Jwt jwt, UUID groupId) {
        return scope(jwt, groupId).owner();
    }
}
