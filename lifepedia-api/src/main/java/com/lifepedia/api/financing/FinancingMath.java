package com.lifepedia.api.financing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Parcelas SAC ou Price com seguro sobre o saldo, tarifa mensal, correção do saldo por índice e amortizações extras. */
public final class FinancingMath {

    public enum AmortizationSystem { SAC, PRICE }

    /** TERM mantém a parcela e encurta o prazo; PAYMENT mantém o prazo e baixa a parcela. */
    public enum ExtraMode { TERM, PAYMENT }

    private static final int MAX_ROWS = 600;
    private static final double DONE = 0.005;

    private FinancingMath() {
    }

    /**
     * Taxas em fração ao mês.
     *
     * @param insuranceRate seguro sobre o saldo (MIP)
     * @param monthlyFee    valor fixo somado a cada parcela (seguro do bem, tarifa)
     * @param correction    correção do saldo em cada mês (TR, IPCA...)
     */
    public record Plan(double principal, double monthlyRate, int months, AmortizationSystem system,
                       double insuranceRate, double monthlyFee, YearMonth firstMonth,
                       Function<YearMonth, Double> correction, List<Extra> extras) {

        public Plan withExtras(List<Extra> extras) {
            return new Plan(principal, monthlyRate, months, system, insuranceRate, monthlyFee, firstMonth, correction, extras);
        }

        public Plan with(double rate, int months, AmortizationSystem system) {
            return new Plan(principal, rate, months, system, insuranceRate, monthlyFee, firstMonth, correction, extras);
        }
    }

    public record Extra(YearMonth month, double amount, ExtraMode mode) {
    }

    /** @param payment parcela com seguro e tarifa; @param charges seguro + tarifa; @param balance saldo depois do mês */
    public record Row(int number, YearMonth month, BigDecimal payment, BigDecimal interest, BigDecimal amortization,
                      BigDecimal charges, BigDecimal extra, BigDecimal balance) {
    }

    /** @param totalPaid parcelas + amortizações extras */
    public record Totals(BigDecimal firstPayment, BigDecimal lastPayment, BigDecimal totalPaid, BigDecimal totalInterest,
                         BigDecimal totalCharges, int months, YearMonth lastMonth) {
    }

    public static List<Row> schedule(Plan plan) {
        Map<YearMonth, List<Extra>> extras = plan.extras().stream().collect(Collectors.groupingBy(Extra::month));
        double i = plan.monthlyRate();
        double balance = plan.principal();
        int remaining = plan.months();
        List<Row> rows = new ArrayList<>();
        YearMonth month = plan.firstMonth();
        for (int n = 1; balance > DONE && remaining > 0 && n <= MAX_ROWS; n++, month = month.plusMonths(1)) {
            balance *= 1 + plan.correction().apply(month);
            double interest = balance * i;
            double amortization = remaining == 1 ? balance
                    : plan.system() == AmortizationSystem.SAC ? balance / remaining : pmt(balance, i, remaining) - interest;
            double charges = balance * plan.insuranceRate() + plan.monthlyFee();
            balance -= amortization;
            remaining--;
            double extra = 0;
            for (Extra e : extras.getOrDefault(month, List.of())) {
                double paid = Math.min(e.amount(), balance);
                if (paid <= 0) continue;
                double before = balance;
                balance -= paid;
                extra += paid;
                if (e.mode() == ExtraMode.TERM && remaining > 0) {
                    remaining = shorterTerm(plan.system(), before, balance, i, remaining);
                }
            }
            rows.add(new Row(n, month, money(interest + amortization + charges), money(interest), money(amortization),
                    money(charges), money(extra), money(Math.max(balance, 0))));
        }
        return rows;
    }

    public static Totals totals(List<Row> rows) {
        if (rows.isEmpty()) {
            return new Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, null);
        }
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal charges = BigDecimal.ZERO;
        for (Row r : rows) {
            paid = paid.add(r.payment()).add(r.extra());
            interest = interest.add(r.interest());
            charges = charges.add(r.charges());
        }
        Row last = rows.getLast();
        return new Totals(rows.getFirst().payment(), last.payment(), paid, interest, charges, rows.size(), last.month());
    }

    /**
     * Custo efetivo total ao mês (fração): taxa que iguala o valor recebido às parcelas pagas.
     *
     * @param received valor liberado descontadas as tarifas do banco; nulo se não houver
     */
    public static Double cet(double received, List<Row> rows) {
        if (received <= 0 || rows.isEmpty()) return null;
        double[] flows = rows.stream().mapToDouble(r -> r.payment().add(r.extra()).doubleValue()).toArray();
        if (npv(received, flows, 0) >= 0) return 0d;
        double low = 0;
        double high = 1;
        for (int k = 0; k < 100; k++) {
            double mid = (low + high) / 2;
            if (npv(received, flows, mid) < 0) low = mid;
            else high = mid;
        }
        return (low + high) / 2;
    }

    /** Primeira parcela por real financiado (sem a tarifa fixa); a parcela é linear no valor financiado. */
    public static double firstPaymentFactor(Plan plan) {
        double c = 1 + plan.correction().apply(plan.firstMonth());
        double base = plan.system() == AmortizationSystem.SAC
                ? 1.0 / plan.months() + plan.monthlyRate()
                : pmt(1, plan.monthlyRate(), plan.months());
        return c * (base + plan.insuranceRate());
    }

    public static double yearly(double monthly) {
        return Math.pow(1 + monthly, 12) - 1;
    }

    public static double monthly(double yearly) {
        return Math.pow(1 + yearly, 1.0 / 12) - 1;
    }

    public static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Fração em porcentagem com 2 casas. */
    public static BigDecimal percent(double fraction) {
        return BigDecimal.valueOf(fraction * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private static double pmt(double balance, double rate, int months) {
        if (rate == 0) return balance / months;
        return balance * rate / (1 - Math.pow(1 + rate, -months));
    }

    /** Prazo restante que mantém a amortização (SAC) ou a parcela (Price) de antes da amortização extra. */
    private static int shorterTerm(AmortizationSystem system, double before, double after, double rate, int remaining) {
        if (after <= DONE) return 0;
        double months;
        if (system == AmortizationSystem.SAC) {
            months = after / (before / remaining);
        } else {
            double payment = pmt(before, rate, remaining);
            months = rate == 0 ? after / payment : -Math.log(1 - after * rate / payment) / Math.log(1 + rate);
        }
        return (int) Math.max(1, Math.min(remaining, Math.ceil(months - 1e-9)));
    }

    private static double npv(double received, double[] flows, double rate) {
        double value = received;
        double factor = 1;
        for (double flow : flows) {
            factor /= 1 + rate;
            value -= flow * factor;
        }
        return value;
    }
}
