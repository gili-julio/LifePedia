package com.lifepedia.api.school;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Todo dia às 19h confere as matérias em andamento (faltas mudam pela Frequência) e avisa o risco uma vez por motivo. */
@Component
public class SchoolRiskJob {

    private final SchoolService school;

    public SchoolRiskJob(SchoolService school) {
        this.school = school;
    }

    @Scheduled(cron = "0 0 19 * * *", zone = "${lifepedia.time.zone}")
    public void run() {
        school.checkRisks();
    }
}
