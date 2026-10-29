package com.lifepedia.api.task;

import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.PageResponse;
import com.lifepedia.api.shared.Paging;
import com.lifepedia.api.task.TaskDtos.CommentRequest;
import com.lifepedia.api.task.TaskDtos.CommentResponse;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Comentários das tarefas de grupo. Qualquer membro comenta; apagar: quem escreveu ou um administrador. */
@Service
public class TaskCommentService {

    static final int MAX_PER_TASK = 1000;
    private static final Map<String, String> SORT = Map.of("createdAt", "c.createdAt");

    private final TaskCommentRepository comments;
    private final TaskService service;
    private final UserRepository users;
    private final TaskNotifier notifier;

    public TaskCommentService(TaskCommentRepository comments, TaskService service, UserRepository users,
                              TaskNotifier notifier) {
        this.comments = comments;
        this.service = service;
        this.users = users;
        this.notifier = notifier;
    }

    /** Mais recentes primeiro por padrão. */
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> page(TaskScope scope, UUID taskId, int page, int size, String sort) {
        Task task = find(scope, taskId);
        var pageable = Paging.pageable(page, size, sort, SORT, "createdAt,desc", "c.id");
        return PageResponse.of(comments.page(task.getId(), pageable), items -> {
            Map<UUID, User> authors = users.findAllById(items.stream().map(TaskComment::getUserId)
                            .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            return items.stream().map(c -> response(c, authors.get(c.getUserId()), scope)).toList();
        });
    }

    @Transactional
    public CommentResponse add(TaskScope scope, UUID taskId, CommentRequest request) {
        Task task = find(scope, taskId);
        if (comments.countByTaskId(task.getId()) >= MAX_PER_TASK) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_PER_TASK + " comentários na tarefa.");
        }
        String body = request.body().trim();
        TaskComment comment = comments.save(new TaskComment(task.getId(), scope.userId(), body));
        // Participantes: quem criou, responsáveis e quem já comentou.
        Set<UUID> people = new LinkedHashSet<>();
        people.add(task.getCreatedBy());
        people.addAll(task.getAssignees());
        people.addAll(task.getRotation());
        people.addAll(comments.authors(task.getId()));
        people.remove(null);
        people.forEach(to -> notifier.comment(task, to, scope.userId(), body));
        return response(comment, users.findById(scope.userId()).orElse(null), scope);
    }

    @Transactional
    public void delete(TaskScope scope, UUID taskId, UUID commentId) {
        Task task = find(scope, taskId);
        TaskComment comment = comments.findByIdAndTaskId(commentId, task.getId())
                .orElseThrow(() -> new NotFoundException("Comentário não encontrado."));
        if (!canDelete(comment, scope)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Só quem escreveu ou um administrador pode apagar.");
        }
        comments.delete(comment);
    }

    private Task find(TaskScope scope, UUID taskId) {
        if (!scope.isGroup()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Comentários só em tarefas de grupo.");
        return service.find(scope.owner(), taskId);
    }

    private static boolean canDelete(TaskComment comment, TaskScope scope) {
        return scope.admin() || scope.userId().equals(comment.getUserId());
    }

    private static CommentResponse response(TaskComment comment, User author, TaskScope scope) {
        return new CommentResponse(comment.getId(), author == null ? null : UserSummary.from(author), comment.getBody(),
                comment.getCreatedAt(), canDelete(comment, scope));
    }
}
