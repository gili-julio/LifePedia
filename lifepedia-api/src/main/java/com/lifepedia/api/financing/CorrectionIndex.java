package com.lifepedia.api.financing;

/**
 * Índice que corrige o saldo devedor todo mês (série do SGS do Banco Central).
 *
 * @param fallback variação mensal (%) usada sem dados guardados
 */
public enum CorrectionIndex {
    NONE(0, 0),
    TR(7811, 0.15),
    IPCA(433, 0.40),
    INCC(7456, 0.45);

    private final int series;
    private final double fallback;

    CorrectionIndex(int series, double fallback) {
        this.series = series;
        this.fallback = fallback;
    }

    public int series() {
        return series;
    }

    public double fallback() {
        return fallback;
    }
}
