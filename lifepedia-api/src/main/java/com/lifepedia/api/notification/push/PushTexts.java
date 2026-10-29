package com.lifepedia.api.notification.push;

import com.lifepedia.api.notification.NotificationType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Texto do push de cada notificação; espelha {@code notification-content.tsx} do front, sem formatação. */
public final class PushTexts {

    private static final String APP = "LifePedia";

    private PushTexts() {
    }

    public static PushMessage of(NotificationType type, Map<String, String> data, UUID groupId) {
        String group = data.get("groupName");
        String title = group == null ? APP : group;
        String actor = data.getOrDefault("actorName", "");
        String task = data.getOrDefault("taskTitle", "");
        String tag = type.name() + ":" + data.getOrDefault("taskId", data.getOrDefault("requestId", ""));
        return switch (type) {
            case TASK_ASSIGNED -> new PushMessage(title, actor + " te deixou responsável por " + task + onDay(data),
                    taskLink(data, groupId), tag, false);
            case TASK_TURN -> new PushMessage(title, "É a sua vez: " + task + onDay(data), taskLink(data, groupId), tag, false);
            case TASK_COMPLETED -> new PushMessage(title, actor + " concluiu " + task + onDay(data),
                    taskLink(data, groupId), tag, false);
            case TASK_COMMENT -> new PushMessage(title, actor + " comentou em " + task + ": “" + data.getOrDefault("preview", "") + "”",
                    taskLink(data, groupId), tag, false);
            case TASK_OVERDUE -> new PushMessage(title, task + onDay(data) + " ficou atrasada", taskLink(data, groupId), tag, false);
            case TASK_REMINDER -> new PushMessage(title, "Lembrete: " + task + " às " + time(data) + onDay(data),
                    taskLink(data, groupId), tag, true);
            case GROUP_INVITE -> new PushMessage(APP, actor + " te chamou para participar do grupo " + group,
                    "/grupos?aba=solicitacoes", tag, false);
            case MEMBER_ACCOUNT_DELETED -> new PushMessage(title, actor + " excluiu a conta e saiu do grupo"
                    + ("true".equals(data.get("promoted")) ? ". Agora você é administrador." : "."),
                    groupId == null ? "/grupos" : "/grupos/" + groupId, tag, false);
            case FRIEND_REQUEST -> new PushMessage(APP, actor + " quer ser seu amigo", "/amigos?aba=pedidos", tag, false);
            case FRIEND_ACCEPTED -> new PushMessage(APP, actor + " aceitou seu pedido de amizade", "/amigos", tag, false);
        };
    }

    /** Mensagem nova no chat; a tag por grupo faz a última substituir as anteriores. */
    public static PushMessage chat(UUID groupId, String groupName, String author, String body) {
        String text = body.length() > 140 ? body.substring(0, 139) + "…" : body;
        return new PushMessage(groupName, author + ": " + text, "/grupos/" + groupId + "?aba=chat", "chat:" + groupId, true);
    }

    private static String taskLink(Map<String, String> data, UUID groupId) {
        String id = URLEncoder.encode(data.getOrDefault("taskId", ""), StandardCharsets.UTF_8);
        return groupId == null ? "/tarefas?tarefa=" + id : "/grupos/" + groupId + "?aba=tarefas&tarefa=" + id;
    }

    private static String onDay(Map<String, String> data) {
        String date = data.get("date");
        if (date == null || date.length() != 10) return "";
        return " (" + date.substring(8, 10) + "/" + date.substring(5, 7) + "/" + date.substring(0, 4) + ")";
    }

    private static String time(Map<String, String> data) {
        String time = data.getOrDefault("time", "");
        return time.length() >= 5 ? time.substring(0, 5) : time;
    }
}
