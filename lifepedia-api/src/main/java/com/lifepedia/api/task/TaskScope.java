package com.lifepedia.api.task;

import java.util.UUID;

/**
 * De onde vem a ação: dono das tarefas e quem age.
 *
 * @param admin pode excluir qualquer tarefa (pessoal: sempre; grupo: administrador)
 */
public record TaskScope(TaskOwner owner, UUID userId, boolean admin) {

    public static TaskScope personal(UUID userId) {
        return new TaskScope(TaskOwner.personal(userId), userId, true);
    }

    public UUID groupId() {
        return owner.groupId();
    }

    public boolean isGroup() {
        return owner.groupId() != null;
    }

    /** Criador ou administrador. */
    public boolean canDelete(Task task) {
        return admin || (userId != null && userId.equals(task.getCreatedBy()));
    }
}
