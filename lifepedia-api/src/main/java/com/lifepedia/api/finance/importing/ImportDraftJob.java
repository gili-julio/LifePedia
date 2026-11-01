package com.lifepedia.api.finance.importing;

import com.lifepedia.api.config.AiProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/** Encerra leituras perdidas e limpa revisões esquecidas. */
@Component
public class ImportDraftJob {

    private static final Duration MARGIN = Duration.ofMinutes(2);

    private final ImportDraftService drafts;
    private final AiProperties properties;
    private final Clock clock;

    public ImportDraftJob(ImportDraftService drafts, AiProperties properties, Clock clock) {
        this.drafts = drafts;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT5M")
    public void failStale() {
        drafts.failStale(OffsetDateTime.now(clock).minus(properties.timeout()).minus(MARGIN));
    }

    @Scheduled(cron = "0 15 4 * * *", zone = "${lifepedia.time.zone}")
    public void purge() {
        drafts.purge(OffsetDateTime.now(clock));
    }
}
