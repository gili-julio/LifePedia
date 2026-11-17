package com.lifepedia.api.vehicle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Contas do módulo (puras): ritmo de uso, km estimado, vencimento por km/data e consumo pelo tanque cheio. */
public final class VehicleMath {

    /** Situação de um vencimento, da melhor para a pior (a ordem vale para os avisos). */
    public enum Stage {
        OK, SOON, DUE
    }

    /**
     * @param kmLeft     km até vencer (negativo = passou)
     * @param daysLeft   dias até a data esperada (negativo = passou)
     * @param expectedOn data esperada: a menor entre a data limite e a estimada pelo ritmo de uso
     */
    public record Due(Stage stage, Integer dueKm, LocalDate dueOn, Integer kmLeft, Integer daysLeft, LocalDate expectedOn) {
    }

    public record Reading(LocalDate date, int km) {
    }

    /** Abastecimento: {@code odometer} e {@code liters} podem faltar (aí quebram a conta). */
    public record Fill(LocalDate date, Integer odometer, BigDecimal liters, boolean fullTank) {
    }

    public record Consumption(LocalDate date, BigDecimal kmPerLiter, int km, BigDecimal liters) {
    }

    static final int WINDOW_DAYS = 180;
    static final int MIN_SPAN_DAYS = 7;
    static final int MAX_PROJECTION_DAYS = 90;

    private VehicleMath() {
    }

    /** Km por dia nas leituras dos últimos 180 dias (ou em todas, se não der 7 dias); nulo sem base. */
    public static Double kmPerDay(List<Reading> readings) {
        if (readings.size() < 2) return null;
        List<Reading> sorted = sorted(readings);
        Reading last = sorted.getLast();
        Reading first = sorted.stream().filter(r -> !r.date().isBefore(last.date().minusDays(WINDOW_DAYS))).findFirst()
                .orElse(sorted.getFirst());
        if (ChronoUnit.DAYS.between(first.date(), last.date()) < MIN_SPAN_DAYS) first = sorted.getFirst();
        long days = ChronoUnit.DAYS.between(first.date(), last.date());
        if (days < MIN_SPAN_DAYS || last.km() <= first.km()) return null;
        return (double) (last.km() - first.km()) / days;
    }

    /** Km de hoje pelo ritmo (até 90 dias depois da última leitura). */
    public static int estimatedKm(int lastKm, LocalDate lastOn, Double kmPerDay, LocalDate today) {
        if (kmPerDay == null || lastOn == null || !today.isAfter(lastOn)) return lastKm;
        long days = Math.min(ChronoUnit.DAYS.between(lastOn, today), MAX_PROJECTION_DAYS);
        return lastKm + (int) Math.round(kmPerDay * days);
    }

    /** Item periódico; nulo se nunca foi feito (sem base para o próximo). */
    public static Due item(Integer intervalKm, Integer intervalMonths, Integer lastKm, LocalDate lastOn, int currentKm,
                           Double kmPerDay, LocalDate today) {
        Integer dueKm = intervalKm != null && lastKm != null ? lastKm + intervalKm : null;
        LocalDate dueOn = intervalMonths != null && lastOn != null ? lastOn.plusMonths(intervalMonths) : null;
        if (dueKm == null && dueOn == null) return null;
        int soonKm = intervalKm == null ? 300 : Math.max(200, intervalKm / 10);
        return due(dueKm, dueOn, currentKm, kmPerDay, today, soonKm, 15);
    }

    public static Due revision(Integer dueKm, LocalDate dueOn, int currentKm, Double kmPerDay, LocalDate today) {
        return due(dueKm, dueOn, currentKm, kmPerDay, today, 500, 30);
    }

    public static Due document(LocalDate dueOn, LocalDate today) {
        return due(null, dueOn, 0, null, today, 0, 30);
    }

    /** Vence ao chegar no km ou na data, o que vier primeiro; "perto" pelo km que falta ou pelos dias. */
    public static Due due(Integer dueKm, LocalDate dueOn, int currentKm, Double kmPerDay, LocalDate today, int soonKm, int soonDays) {
        Integer kmLeft = dueKm == null ? null : dueKm - currentKm;
        LocalDate byKm = kmLeft == null || kmPerDay == null || kmPerDay <= 0 ? null
                : today.plusDays(kmLeft <= 0 ? 0 : (long) Math.ceil(kmLeft / kmPerDay));
        LocalDate expected = byKm == null ? dueOn : dueOn == null || byKm.isBefore(dueOn) ? byKm : dueOn;
        Integer daysLeft = expected == null ? null : (int) ChronoUnit.DAYS.between(today, expected);
        boolean due = kmLeft != null && kmLeft <= 0 || dueOn != null && !dueOn.isAfter(today);
        boolean soon = kmLeft != null && kmLeft <= soonKm || daysLeft != null && daysLeft <= soonDays;
        Stage stage = due ? Stage.DUE : soon ? Stage.SOON : Stage.OK;
        return new Due(stage, dueKm, dueOn, kmLeft, daysLeft, expected);
    }

    /** Média entre dois tanques cheios (somando os parciais do meio); abastecimento sem litros quebra a conta. */
    public static List<Consumption> consumption(List<Fill> fills) {
        List<Fill> sorted = fills.stream().filter(f -> f.odometer() != null)
                .sorted(Comparator.comparing(Fill::odometer).thenComparing(Fill::date)).toList();
        List<Consumption> points = new ArrayList<>();
        Integer startKm = null;
        BigDecimal liters = BigDecimal.ZERO;
        for (Fill fill : sorted) {
            if (fill.liters() == null || fill.liters().signum() <= 0) {
                startKm = null;
                continue;
            }
            if (startKm == null) {
                if (fill.fullTank()) {
                    startKm = fill.odometer();
                    liters = BigDecimal.ZERO;
                }
                continue;
            }
            liters = liters.add(fill.liters());
            if (fill.fullTank()) {
                int km = fill.odometer() - startKm;
                if (km > 0) {
                    points.add(new Consumption(fill.date(), BigDecimal.valueOf(km).divide(liters, 2, RoundingMode.HALF_UP), km,
                            liters));
                }
                startKm = fill.odometer();
                liters = BigDecimal.ZERO;
            }
        }
        return points;
    }

    /** Km rodados entre as datas pelas leituras (a última antes do início conta como ponto de partida). */
    public static Integer kmDriven(List<Reading> readings, LocalDate from, LocalDate to) {
        List<Reading> sorted = sorted(readings);
        Integer start = null;
        Integer end = null;
        for (Reading r : sorted) {
            if (r.date().isBefore(from)) {
                start = r.km();
            } else if (!r.date().isAfter(to)) {
                if (start == null) start = r.km();
                end = r.km();
            }
        }
        if (start == null || end == null || end <= start) return null;
        return end - start;
    }

    private static List<Reading> sorted(List<Reading> readings) {
        return readings.stream().sorted(Comparator.comparing(Reading::date).thenComparingInt(Reading::km)).toList();
    }
}
