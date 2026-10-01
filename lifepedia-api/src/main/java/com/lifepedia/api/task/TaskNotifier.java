package com.lifepedia.api.task;

import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Avisos das tarefas (de grupo e lembretes); nunca avisa quem agiu nem quem não é mais membro. */
@Component
public class TaskNotifier {

    private static final int PREVIEW_MAX = 100;

    private final NotificationService notifications;
    private final UserRepository users;
    private final GroupMemberRepository members;
    private final GroupAccess access;

    public TaskNotifier(NotificationService notifications, UserRepository users, GroupMemberRepository members,
                        GroupAccess access) {
        this.notifications = notifications;
        this.users = users;
        this.members = members;
        this.access = access;
    }

    void assigned(Task task, LocalDate date, UUID to, UUID actorId) {
        send(NotificationType.TASK_ASSIGNED, task, date, to, actorId, task.getTitle(), Map.of());
    }

    void turn(Task task, LocalDate date, UUID to, UUID actorId) {
        send(NotificationType.TASK_TURN, task, date, to, actorId, task.getTitle(), Map.of());
    }

    void completed(Task task, LocalDate date, String title, UUID to, UUID actorId) {
        send(NotificationType.TASK_COMPLETED, task, date, to, actorId, title, Map.of());
    }

    void comment(Task task, UUID to, UUID actorId, String body) {
        String preview = body.length() > PREVIEW_MAX ? body.substring(0, PREVIEW_MAX - 1) + "…" : body;
        send(NotificationType.TASK_COMMENT, task, null, to, actorId, task.getTitle(), Map.of("preview", preview));
    }

    void overdue(Task task, LocalDate date, String title, UUID to) {
        send(NotificationType.TASK_OVERDUE, task, date, to, null, title, Map.of());
    }

    /** Lembrete para cada pessoa (tarefa pessoal: só o dono); devolve quantos avisos. */
    int reminder(Task task, LocalDate shown, LocalTime time, String title, Collection<UUID> to) {
        Map<String, String> data = new HashMap<>();
        data.put("taskId", task.getId().toString());
        data.put("taskTitle", title);
        data.put("date", shown.toString());
        data.put("time", time.toString());
        data.put("minutes", String.valueOf(task.getReminderMinutes()));
        if (task.getGroupId() != null) data.put("groupName", access.group(task.getGroupId()).getName());
        int sent = 0;
        for (UUID userId : to) {
            boolean allowed = task.getGroupId() == null
                    ? userId.equals(task.getUserId())
                    : members.existsByGroupIdAndUserId(task.getGroupId(), userId);
            if (!allowed) continue;
            notifications.notify(userId, NotificationType.TASK_REMINDER, null, task.getGroupId(), data);
            sent++;
        }
        return sent;
    }

    private void send(NotificationType type, Task task, LocalDate date, UUID to, UUID actorId, String title,
                      Map<String, String> extra) {
        if (task.getGroupId() == null || to == null || to.equals(actorId)
                || !members.existsByGroupIdAndUserId(task.getGroupId(), to)) return;
        Map<String, String> data = new HashMap<>(extra);
        data.put("groupName", access.group(task.getGroupId()).getName());
        data.put("taskId", task.getId().toString());
        data.put("taskTitle", title);
        if (date != null) data.put("date", date.toString());
        if (actorId != null) {
            User actor = users.findById(actorId).orElse(null);
            if (actor != null) {
                data.put("actorName", actor.getName());
                data.put("actorUsername", actor.getUsername() == null ? "" : actor.getUsername());
            }
        }
        notifications.notify(to, type, actorId, task.getGroupId(), data);
    }
}
