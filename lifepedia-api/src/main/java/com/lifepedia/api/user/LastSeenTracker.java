package com.lifepedia.api.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Grava o último acesso de quem usa a API, no máximo uma vez a cada {@link #INTERVAL} por pessoa. */
@Component
public class LastSeenTracker implements HandlerInterceptor {

    static final Duration INTERVAL = Duration.ofMinutes(10);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final UserRepository users;
    private final Map<UUID, Instant> written = new ConcurrentHashMap<>();

    public LastSeenTracker(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) touch(jwt.getToken().getSubject());
        return true;
    }

    private void touch(String subject) {
        UUID id;
        try {
            id = UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            return;
        }
        Instant now = Instant.now();
        Instant last = written.get(id);
        if (last != null && last.isAfter(now.minus(INTERVAL))) return;
        written.put(id, now);
        if (written.size() > CLEANUP_THRESHOLD) written.values().removeIf(t -> t.isBefore(now.minus(INTERVAL)));
        users.touchLastSeen(id, OffsetDateTime.now());
    }
}
