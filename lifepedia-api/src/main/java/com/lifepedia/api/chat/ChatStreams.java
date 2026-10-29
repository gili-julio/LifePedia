package com.lifepedia.api.chat;

import com.lifepedia.api.chat.ChatDtos.ChatEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Conexões SSE abertas por usuário (uma por aba, até {@value #MAX_PER_USER}). Fica em memória: vale para uma instância
 * da API só (com mais de uma, trocar por um broker).
 */
@Component
public class ChatStreams {

    static final int MAX_PER_USER = 5;
    private static final long TIMEOUT = Duration.ofMinutes(30).toMillis();
    private static final Logger log = LoggerFactory.getLogger(ChatStreams.class);

    private final Map<UUID, Deque<SseEmitter>> byUser = new ConcurrentHashMap<>();

    /** Abre o stream; a aba mais antiga cai se passar do limite (o front reconecta sozinho quando precisa). */
    public SseEmitter open(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        Deque<SseEmitter> mine = byUser.computeIfAbsent(userId, id -> new ConcurrentLinkedDeque<>());
        mine.addLast(emitter);
        while (mine.size() > MAX_PER_USER) {
            SseEmitter oldest = mine.pollFirst();
            if (oldest != null) oldest.complete();
        }
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(userId, emitter, SseEmitter.event().comment("ok"));
        return emitter;
    }

    /** Depois do commit: manda a mensagem (nova, editada ou apagada) para quem é membro. */
    @TransactionalEventListener
    public void onMessage(ChatEvent event) {
        deliver(event.recipients(), event);
    }

    /** Mantém a conexão viva atrás de proxies (nginx/Traefik fecham conexão parada). */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        byUser.forEach((userId, emitters) -> emitters.forEach(e -> send(userId, e, SseEmitter.event().comment("ping"))));
    }

    void deliver(Collection<UUID> userIds, ChatEvent event) {
        for (UUID userId : userIds) {
            Deque<SseEmitter> emitters = byUser.get(userId);
            if (emitters == null) continue;
            emitters.forEach(e -> send(userId, e,
                    SseEmitter.event().name("message").data(event.message(), MediaType.APPLICATION_JSON)));
        }
    }

    private void send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            log.debug("Stream do chat fechado: {}", e.getMessage());
            remove(userId, emitter);
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        byUser.computeIfPresent(userId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
