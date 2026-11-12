package com.lifepedia.api.delivery;

import com.lifepedia.api.delivery.Track17Client.Quota;
import com.lifepedia.api.delivery.Track17Client.TrackException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Cota do 17TRACK para o painel do admin (guardada por 5 min). Chamar fora de transação. */
@Component
public class TrackingQuota {

    /** @param quota nula se desligado ou com erro */
    public record Usage(boolean enabled, Quota quota, String error) {
    }

    private static final Duration CACHE = Duration.ofMinutes(5);

    private final Track17Client client;
    private final Clock clock;
    private volatile Usage last;
    private volatile Instant lastAt = Instant.EPOCH;

    public TrackingQuota(Track17Client client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public Usage current() {
        if (!client.enabled()) return new Usage(false, null, null);
        Instant now = Instant.now(clock);
        if (last != null && lastAt.plus(CACHE).isAfter(now)) return last;
        Usage usage;
        try {
            usage = new Usage(true, client.quota(), null);
        } catch (TrackException e) {
            usage = new Usage(true, null, e.getMessage());
        }
        last = usage;
        lastAt = now;
        return usage;
    }
}
