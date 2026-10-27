package com.lifepedia.api.group;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Escrita com sucesso em {@code /api/groups/{groupId}/...} atualiza a última atividade do grupo. */
@Component
public class GroupActivityInterceptor implements HandlerInterceptor {

    private final GroupActivity activity;

    public GroupActivityInterceptor(GroupActivity activity) {
        this.activity = activity;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (ex != null || response.getStatus() >= 400 || HttpMethod.GET.matches(request.getMethod())) return;
        if (!(handler instanceof HandlerMethod method) || method.hasMethodAnnotation(NoGroupActivity.class)) return;
        if (!(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> vars)) return;
        if (!(vars.get("groupId") instanceof String groupId)) return;
        activity.touch(UUID.fromString(groupId), OffsetDateTime.now());
    }
}
