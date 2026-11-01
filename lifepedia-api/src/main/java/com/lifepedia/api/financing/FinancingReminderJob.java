package com.lifepedia.api.financing;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Todo dia às 8h avisa das parcelas e gastos do bem que vencem nos dias escolhidos em cada financiamento. */
@Component
public class FinancingReminderJob {

    private final FinancingService financings;
    private final Clock clock;

    public FinancingReminderJob(FinancingService financings, Clock clock) {
        this.financings = financings;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "${lifepedia.time.zone}")
    public void run() {
        financings.sendReminders(LocalDate.now(clock));
    }
}
