package com.lifepedia.api.financing;

import org.springframework.stereotype.Component;

/** Taxas de mercado para outros módulos: média mensal (fração) dos últimos 12 meses do Banco Central. Chamar fora de transação. */
@Component
public class MarketRates {

    /** @param fallback algum índice sem dados do Banco Central (usou o padrão) */
    public record Rates(double cdi, double selic, double ipca, double tr, boolean fallback) {
    }

    private final FinancingRateService rates;

    public MarketRates(FinancingRateService rates) {
        this.rates = rates;
    }

    public Rates current() {
        boolean fallback = !rates.known(CorrectionIndex.CDI) || !rates.known(CorrectionIndex.SELIC)
                || !rates.known(CorrectionIndex.IPCA);
        return new Rates(rates.projection(CorrectionIndex.CDI), rates.projection(CorrectionIndex.SELIC),
                rates.projection(CorrectionIndex.IPCA), rates.projection(CorrectionIndex.TR), fallback);
    }
}
