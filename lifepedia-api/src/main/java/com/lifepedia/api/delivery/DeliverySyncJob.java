package com.lifepedia.api.delivery;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Atualiza as entregas na vez delas (a cada 15 min) e arquiva as entregues há uma semana (4h). */
@Component
class DeliverySyncJob {

    private final DeliverySyncService sync;

    DeliverySyncJob(DeliverySyncService sync) {
        this.sync = sync;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
    void syncDue() {
        sync.syncDue();
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "${lifepedia.time.zone}")
    void archive() {
        sync.archiveDelivered();
    }
}
