package com.lifepedia.api.task;

import java.util.UUID;

/** Dono das tarefas, listas e etiquetas: um usuário (pessoal) ou um grupo; o outro campo é nulo. */
public record TaskOwner(UUID userId, UUID groupId) {

    public static TaskOwner personal(UUID userId) {
        return new TaskOwner(userId, null);
    }

    public static TaskOwner group(UUID groupId) {
        return new TaskOwner(null, groupId);
    }

    public boolean owns(UUID userId, UUID groupId) {
        return this.userId != null ? this.userId.equals(userId) : this.groupId.equals(groupId);
    }
}
