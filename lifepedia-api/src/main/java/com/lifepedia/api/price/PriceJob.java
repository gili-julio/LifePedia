package com.lifepedia.api.price;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Consulta diária dos preços (às 8h30, até a cota do dia). */
@Component
public class PriceJob {

    private final PriceCheckService checks;

    public PriceJob(PriceCheckService checks) {
        this.checks = checks;
    }

    @Scheduled(cron = "0 30 8 * * *", zone = "${lifepedia.time.zone}")
    public void run() {
        checks.checkDue();
    }
}
