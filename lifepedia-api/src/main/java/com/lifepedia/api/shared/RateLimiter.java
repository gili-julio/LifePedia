package com.lifepedia.api.shared;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Janela deslizante em memória, por chave. Vale para uma instância da API. */
@Component
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Registra um evento e diz se ainda está dentro do limite. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        Deque<Long> events = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (events) {
            evictOld(events, now, window);
            if (events.size() >= limit) return false;
            events.addLast(now);
        }
        if (hits.size() > CLEANUP_THRESHOLD) cleanup(window);
        return true;
    }

    /** Eventos na janela, sem registrar um novo. */
    public int count(String key, Duration window) {
        Deque<Long> events = hits.get(key);
        if (events == null) return 0;
        synchronized (events) {
            evictOld(events, clock.millis(), window);
            return events.size();
        }
    }

    public void record(String key) {
        Deque<Long> events = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (events) {
            events.addLast(clock.millis());
        }
    }

    public void reset(String key) {
        hits.remove(key);
    }

    private static void evictOld(Deque<Long> events, long now, Duration window) {
        long cutoff = now - window.toMillis();
        while (!events.isEmpty() && events.peekFirst() <= cutoff) events.pollFirst();
    }

    private void cleanup(Duration window) {
        long now = clock.millis();
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                evictOld(e.getValue(), now, window);
                return e.getValue().isEmpty();
            }
        });
    }
}
