package com.lifepedia.api.finance.simulation;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Change;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Point;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Summary;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Yield;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Conta do simulador do financeiro (pura): saldo mês a mês com e sem mudanças e investimento com IR por aporte. */
public final class FinanceSimulator {

    /**
     * @param base        saldo previsto de cada mês sem mudanças (fixos + média dos ocasionais)
     * @param investment  nulo = sem investimento
     * @param inflation   IPCA ao mês (fração), usado só com {@code real}
     */
    public record Plan(YearMonth start, double startBalance, double[] base, List<Change> changes, Invest investment,
                       double inflation, boolean real) {
    }

    /** @param rate rendimento ao mês (fração) */
    public record Invest(double initial, double monthly, double rate, boolean tax, boolean fromBalance) {
    }

    public record Outcome(List<Point> points, Summary summary) {
    }

    /** Médias mensais (fração) dos índices. */
    public record Market(double cdi, double selic, double ipca, double tr) {
    }

    private static final double SAVINGS_SELIC_LIMIT = 0.085;

    private FinanceSimulator() {
    }

    public static Outcome run(Plan plan) {
        int months = plan.base().length;
        double[] delta = changes(plan.start(), months, plan.changes());
        Invest inv = plan.investment();
        List<double[]> lots = new ArrayList<>();
        if (inv != null && inv.initial() > 0) lots.add(new double[]{inv.initial(), inv.initial(), 0});

        double current = plan.startBalance();
        double cash = plan.startBalance();
        double deflator = 1;
        YearMonth firstNegativeCurrent = null;
        YearMonth firstNegativeSimulated = null;
        List<Point> points = new ArrayList<>(months);
        double gross = 0;
        double contributed = 0;
        double tax = 0;
        for (int t = 0; t < months; t++) {
            YearMonth month = plan.start().plusMonths(t);
            current += plan.base()[t];
            cash += plan.base()[t] + delta[t];
            if (inv != null) {
                if (inv.monthly() > 0) {
                    lots.add(new double[]{inv.monthly(), inv.monthly(), t});
                    if (inv.fromBalance()) cash -= inv.monthly();
                }
                gross = 0;
                contributed = 0;
                tax = 0;
                for (double[] lot : lots) {
                    lot[0] *= 1 + inv.rate();
                    gross += lot[0];
                    contributed += lot[1];
                    if (inv.tax()) tax += Math.max(0, lot[0] - lot[1]) * taxRate(t - (int) lot[2] + 1);
                }
            }
            double simulated = cash + gross - tax;
            if (firstNegativeCurrent == null && current < 0) firstNegativeCurrent = month;
            if (firstNegativeSimulated == null && simulated < 0) firstNegativeSimulated = month;
            if (plan.real()) deflator *= 1 + plan.inflation();
            points.add(new Point(month, money(current / deflator), money(simulated / deflator), money(cash / deflator),
                    money(contributed / deflator), money((gross - contributed) / deflator), money(tax / deflator),
                    money((gross - tax) / deflator)));
        }
        Point last = points.getLast();
        Summary summary = new Summary(last.current(), last.simulated(), last.simulated().subtract(last.current()),
                last.contributed(), last.interest(), last.tax(), last.invested(),
                firstNegativeCurrent, firstNegativeSimulated,
                inv == null ? null : percent(inv.rate()), inv == null ? null : percent(yearly(inv.rate())));
        return new Outcome(points, summary);
    }

    /** Rendimento ao mês (fração). {@code rate}: % do CDI/Selic, taxa a.a. (IPCA + ou fixa); a poupança segue a regra. */
    public static double monthlyRate(Yield source, double rate, Market market) {
        return switch (source) {
            case CDI -> market.cdi() * rate / 100;
            case SELIC -> market.selic() * rate / 100;
            case IPCA -> (1 + market.ipca()) * monthly(rate / 100) - 1;
            case FIXED -> monthly(rate / 100) - 1;
            case SAVINGS -> {
                double selicYear = yearly(market.selic());
                double base = selicYear > SAVINGS_SELIC_LIMIT ? 0.005 : monthly(selicYear * 0.7) - 1;
                yield base + market.tr();
            }
        };
    }

    /** IR regressivo pelo tempo do aporte (meses ~ 180, 360 e 720 dias). */
    static double taxRate(int months) {
        if (months <= 6) return 0.225;
        if (months <= 12) return 0.20;
        if (months <= 24) return 0.175;
        return 0.15;
    }

    public static double yearly(double monthly) {
        return Math.pow(1 + monthly, 12) - 1;
    }

    public static BigDecimal percent(double fraction) {
        return BigDecimal.valueOf(fraction * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private static double[] changes(YearMonth start, int months, List<Change> changes) {
        double[] delta = new double[months];
        if (changes == null) return delta;
        for (Change c : changes) {
            double value = c.type() == EntryType.INCOME ? c.amount().doubleValue() : -c.amount().doubleValue();
            int from = Math.max(0, offset(start, c.start()));
            int to = Boolean.TRUE.equals(c.once()) ? offset(start, c.start())
                    : c.end() == null ? months - 1 : Math.min(months - 1, offset(start, c.end()));
            for (int t = from; t <= to && t < months; t++) delta[t] += value;
        }
        return delta;
    }

    private static int offset(YearMonth start, YearMonth month) {
        return (month.getYear() - start.getYear()) * 12 + month.getMonthValue() - start.getMonthValue();
    }

    private static double monthly(double yearly) {
        return Math.pow(1 + yearly, 1d / 12);
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
