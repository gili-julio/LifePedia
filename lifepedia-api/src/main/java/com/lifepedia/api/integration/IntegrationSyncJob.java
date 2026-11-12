package com.lifepedia.api.integration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sincroniza as contas na vez delas (a cada 5 min) e avisa tokens vencendo (9h). */
@Component
class IntegrationSyncJob {

    private final IntegrationSyncService sync;

    IntegrationSyncJob(IntegrationSyncService sync) {
        this.sync = sync;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    void syncDue() {
        sync.syncDue();
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "${lifepedia.time.zone}")
    void warnExpiring() {
        sync.warnExpiring();
    }
}
