package com.lifepedia.api.financing;

/**
 * Modalidade de crédito pessoa física no Banco Central (código da consulta de taxas por banco).
 *
 * @param fallbackRate % ao mês usado enquanto não há taxa do Banco Central guardada
 */
public enum Modality {
    VEHICLE("401101", "D", "Aquisição de veículos", 1.80),
    HOUSING("903201", "M", "Financiamento imobiliário com taxas de mercado (+ TR)", 0.95),
    OTHER("402101", "D", "Aquisição de outros bens", 2.50);

    private final String code;
    private final String period;
    private final String label;
    private final double fallbackRate;

    Modality(String code, String period, String label, double fallbackRate) {
        this.code = code;
        this.period = period;
        this.label = label;
        this.fallbackRate = fallbackRate;
    }

    public String code() {
        return code;
    }

    /** "D" = divulgação semanal (média de 5 dias úteis), "M" = mensal. */
    public String period() {
        return period;
    }

    public String label() {
        return label;
    }

    public double fallbackRate() {
        return fallbackRate;
    }
}
