package com.lifepedia.api.task;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Item do checklist da tarefa; marcado por ocorrência ({@code task_subtask_checks}). */
@Entity
@Table(name = "task_subtasks")
public class TaskSubtask extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private int position;

    protected TaskSubtask() {
    }

    public TaskSubtask(UUID taskId, String title, int position) {
        this.taskId = taskId;
        set(title, position);
    }

    public void set(String title, int position) {
        this.title = title;
        this.position = position;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public int getPosition() {
        return position;
    }
}
