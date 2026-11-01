package com.lifepedia.api.financing;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Atualiza as taxas dos bancos e os índices do Banco Central um minuto depois de subir e a cada 6 horas. */
@Component
public class FinancingRateJob {

    private final FinancingRateService rates;

    public FinancingRateJob(FinancingRateService rates) {
        this.rates = rates;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 21_600_000)
    public void run() {
        rates.refreshAll();
    }
}
