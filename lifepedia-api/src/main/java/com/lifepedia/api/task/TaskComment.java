package com.lifepedia.api.task;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Comentário numa tarefa de grupo. */
@Entity
@Table(name = "task_comments")
public class TaskComment extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    /** Nulo se a conta foi excluída. */
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 2000, updatable = false)
    private String body;

    protected TaskComment() {
    }

    public TaskComment(UUID taskId, UUID userId, String body) {
        this.taskId = taskId;
        this.userId = userId;
        this.body = body;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBody() {
        return body;
    }
}
