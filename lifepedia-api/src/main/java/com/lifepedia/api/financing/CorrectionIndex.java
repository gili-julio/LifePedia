package com.lifepedia.api.financing;

/**
 * Índice mensal do SGS do Banco Central. Os de {@code correction} corrigem o saldo devedor; CDI e Selic só rendem
 * (simulador do financeiro, por {@link MarketRates}).
 *
 * @param fallback variação mensal (%) usada sem dados guardados
 */
public enum CorrectionIndex {
    NONE(0, 0, true),
    TR(7811, 0.15, true),
    IPCA(433, 0.40, true),
    INCC(7456, 0.45, true),
    CDI(4391, 1.0, false),
    SELIC(4390, 1.0, false);

    private final int series;
    private final double fallback;
    private final boolean correction;

    CorrectionIndex(int series, double fallback, boolean correction) {
        this.series = series;
        this.fallback = fallback;
        this.correction = correction;
    }

    public int series() {
        return series;
    }

    public double fallback() {
        return fallback;
    }

    public boolean isCorrection() {
        return correction;
    }
}
