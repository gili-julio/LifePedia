package com.lifepedia.api.notification;

/** Tipos de notificação. Um tipo novo precisa do texto correspondente no front (features/notifications). */
public enum NotificationType {
    /** data: actorName, actorUsername, groupName, inviteId */
    GROUP_INVITE,
    /** data: actorName, actorUsername, groupName, promoted ("true" para quem virou admin) */
    MEMBER_ACCOUNT_DELETED,
    /** data: actorName, actorUsername, groupName, taskId, taskTitle, date (se houver) */
    TASK_ASSIGNED,
    /** Vez no rodízio. data: actorName, actorUsername, groupName, taskId, taskTitle, date */
    TASK_TURN,
    /** Alguém concluiu uma tarefa da pessoa. data: actorName, actorUsername, groupName, taskId, taskTitle, date */
    TASK_COMPLETED,
    /** data: actorName, actorUsername, groupName, taskId, taskTitle, preview */
    TASK_COMMENT,
    /** Ocorrência que ficou atrasada (aviso diário). data: groupName, taskId, taskTitle, date */
    TASK_OVERDUE,
    /** Lembrete (pessoal ou de grupo). data: taskId, taskTitle, date (dia mostrado), time, minutes, groupName (grupo) */
    TASK_REMINDER
}
