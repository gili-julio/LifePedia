package com.lifepedia.api.task;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Lista (projeto) de tarefas, com cor. */
@Entity
@Table(name = "task_lists")
public class TaskList extends BaseEntity {

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "group_id", updatable = false)
    private UUID groupId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;

    protected TaskList() {
    }

    public TaskList(TaskOwner owner, String name, String color) {
        this.userId = owner.userId();
        this.groupId = owner.groupId();
        set(name, color);
    }

    public void set(String name, String color) {
        this.name = name;
        this.color = color.toLowerCase();
    }

    public boolean ownedBy(TaskOwner owner) {
        return owner.owns(userId, groupId);
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }
}
