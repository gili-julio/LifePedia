package com.lifepedia.api.architecture;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A cada hora: versões reservadas cujo arquivo não chegou e links vencidos há mais de 30 dias. */
@Component
public class ArchitectureCleanupJob {

    private final ArchitectureService architecture;
    private final ArchShareService sharing;

    public ArchitectureCleanupJob(ArchitectureService architecture, ArchShareService sharing) {
        this.architecture = architecture;
        this.sharing = sharing;
    }

    @Scheduled(cron = "0 15 * * * *", zone = "${lifepedia.time.zone}")
    public void run() {
        architecture.purgePending();
        sharing.purgeExpiredLinks();
    }
}
