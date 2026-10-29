package com.lifepedia.api.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Toda madrugada apaga as sessões vencidas. */
@Component
public class SessionCleanupJob {

    private final UserSessionRepository sessions;

    public SessionCleanupJob(UserSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "${lifepedia.time.zone}")
    public void run() {
        sessions.deleteExpired(Instant.now());
    }
}
