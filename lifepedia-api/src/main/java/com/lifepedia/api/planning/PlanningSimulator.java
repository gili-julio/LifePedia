package com.lifepedia.api.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Simulação mês a mês (em centavos): saldo acumulado = reserva + saldos previstos − pagamentos dos itens.
 * Itens considerados entram na ordem recebida (prioridade); cada um só usa o que sobra depois dos anteriores.
 */
final class PlanningSimulator {

    /** Horizonte máximo: 20 anos. */
    static final int MAX_MONTHS = 240;
    /** Meses simulados além do mostrado, só para achar quando cada item caberia. */
    static final int LOOKAHEAD_MONTHS = 60;
    private static final int MIN_MONTHS = 24;

    record Entry(UUID id, BigDecimal amount, YearMonth target, int installments, boolean considered) {
    }

    /**
     * @param shortfall quanto falta para caber sem atrapalhar os itens de maior prioridade
     * @param earliest  primeiro mês em que caberia (nulo: não cabe no horizonte)
     */
    record ItemOutcome(BigDecimal shortfall, YearMonth earliest, BigDecimal monthlySaving, BigDecimal installment,
                       boolean overdue) {
    }

    record Point(YearMonth month, BigDecimal base, BigDecimal planned, BigDecimal withoutPlan, BigDecimal balance,
                 List<UUID> due) {
    }

    record Result(List<Point> timeline, Map<UUID, ItemOutcome> items, int count, BigDecimal total,
                  BigDecimal shortfall, BigDecimal lowestBalance, YearMonth lowestMonth,
                  BigDecimal endBalance, YearMonth endMonth) {
    }

    private PlanningSimulator() {
    }

    /** Último mês simulado: pelo menos 24 meses e até o fim do último pagamento, limitado a {@link #MAX_MONTHS}. */
    static YearMonth horizon(YearMonth start, List<Entry> entries) {
        YearMonth end = start.plusMonths(MIN_MONTHS - 1L);
        for (Entry e : entries) {
            YearMonth last = effectiveTarget(start, e.target()).plusMonths(e.installments() - 1L);
            if (last.isAfter(end)) end = last;
        }
        YearMonth max = start.plusMonths(MAX_MONTHS - 1L);
        return end.isAfter(max) ? max : end;
    }

    static Result run(YearMonth start, List<BigDecimal> base, BigDecimal reserve, List<Entry> entries) {
        return run(start, base, reserve, entries, base.size());
    }

    /**
     * @param base    saldo previsto de cada mês a partir de {@code start}; pode ir além do mostrado, para achar
     *                em que mês cada item caberia
     * @param display meses mostrados (linha do tempo, ponto mais baixo e saldo final)
     */
    static Result run(YearMonth start, List<BigDecimal> base, BigDecimal reserve, List<Entry> entries, int display) {
        int n = base.size();
        long[] baseCents = new long[n];
        long[] withoutPlan = new long[n];
        long[] free = new long[n];
        long running = cents(reserve);
        for (int i = 0; i < n; i++) {
            baseCents[i] = cents(base.get(i));
            running += baseCents[i];
            withoutPlan[i] = running;
            free[i] = running;
        }
        long[] planned = new long[n];
        List<List<UUID>> due = new ArrayList<>(n);
        for (int i = 0; i < n; i++) due.add(new ArrayList<>());

        Map<UUID, ItemOutcome> outcomes = new HashMap<>();
        long total = 0;
        int count = 0;
        int lastPayment = -1;
        for (Entry e : entries) {
            if (!e.considered()) continue;
            int t = index(start, e.target());
            long[] pay = installments(cents(e.amount()), e.installments());
            outcomes.put(e.id(), outcome(start, e, t, pay, free));
            for (int m = t, k = 0; m < n; m++) {
                if (k < pay.length) {
                    planned[m] += pay[k];
                    k++;
                }
                free[m] -= sum(pay, k);
            }
            if (t < n) due.get(t).add(e.id());
            lastPayment = Math.max(lastPayment, Math.min(display - 1, t + pay.length - 1));
            total += cents(e.amount());
            count++;
        }
        // Não considerados: quanto faltaria se entrassem por último.
        for (Entry e : entries) {
            if (e.considered()) continue;
            int t = index(start, e.target());
            outcomes.put(e.id(), outcome(start, e, t, installments(cents(e.amount()), e.installments()), free));
        }

        int lowest = 0;
        for (int i = 1; i < display; i++) if (free[i] < free[lowest]) lowest = i;
        int end = lastPayment >= 0 ? lastPayment : display - 1;

        List<Point> timeline = new ArrayList<>(display);
        for (int i = 0; i < display; i++) {
            timeline.add(new Point(start.plusMonths(i), money(baseCents[i]), money(planned[i]), money(withoutPlan[i]),
                    money(free[i]), List.copyOf(due.get(i))));
        }
        return new Result(timeline, outcomes, count, money(total), money(Math.max(0, -free[lowest])),
                money(free[lowest]), start.plusMonths(lowest), money(free[end]), start.plusMonths(end));
    }

    private static ItemOutcome outcome(YearMonth start, Entry e, int t, long[] pay, long[] free) {
        long shortfall = extraDeficit(free, t, pay);
        int earliest = -1;
        for (int s = 0; s + pay.length <= free.length; s++) {
            if (extraDeficit(free, s, pay) == 0) {
                earliest = s;
                break;
            }
        }
        boolean cash = pay.length == 1;
        long monthly = cash ? ceilDiv(pay[0], t + 1L) : 0;
        return new ItemOutcome(money(shortfall), earliest < 0 ? null : start.plusMonths(earliest),
                cash ? money(monthly) : null, cash ? null : money(pay[0]), e.target().isBefore(start));
    }

    /** Déficit a mais causado pelos pagamentos a partir do mês {@code t} (0 = cabe). */
    private static long extraDeficit(long[] free, int t, long[] pay) {
        long before = Long.MAX_VALUE;
        long after = Long.MAX_VALUE;
        long paid = 0;
        for (int m = t, k = 0; m < free.length; m++, k++) {
            if (k < pay.length) paid += pay[k];
            before = Math.min(before, free[m]);
            after = Math.min(after, free[m] - paid);
        }
        if (before == Long.MAX_VALUE) return 0;
        return Math.max(0, -after) - Math.max(0, -before);
    }

    /** Parcelas iguais; a última leva a diferença do arredondamento. */
    static long[] installments(long totalCents, int count) {
        long[] pay = new long[count];
        long part = totalCents / count;
        for (int i = 0; i < count; i++) pay[i] = part;
        pay[count - 1] = totalCents - part * (count - 1);
        return pay;
    }

    private static long sum(long[] pay, int upTo) {
        long total = 0;
        for (int i = 0; i < upTo; i++) total += pay[i];
        return total;
    }

    private static int index(YearMonth start, YearMonth target) {
        YearMonth effective = effectiveTarget(start, target);
        return (effective.getYear() - start.getYear()) * 12 + effective.getMonthValue() - start.getMonthValue();
    }

    /** Prazo já passado conta como o mês atual. */
    private static YearMonth effectiveTarget(YearMonth start, YearMonth target) {
        return target.isBefore(start) ? start : target;
    }

    private static long ceilDiv(long value, long divisor) {
        return -Math.floorDiv(-value, divisor);
    }

    private static long cents(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).unscaledValue().longValueExact();
    }

    private static BigDecimal money(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
