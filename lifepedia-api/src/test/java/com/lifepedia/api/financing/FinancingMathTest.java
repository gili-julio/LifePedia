package com.lifepedia.api.financing;

import com.lifepedia.api.financing.FinancingMath.AmortizationSystem;
import com.lifepedia.api.financing.FinancingMath.Extra;
import com.lifepedia.api.financing.FinancingMath.ExtraMode;
import com.lifepedia.api.financing.FinancingMath.Plan;
import com.lifepedia.api.financing.FinancingMath.Row;
import com.lifepedia.api.financing.FinancingMath.Totals;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FinancingMathTest {

    private static final YearMonth START = YearMonth.of(2026, 1);

    @Test
    void priceKeepsTheSamePayment() {
        List<Row> rows = FinancingMath.schedule(plan(10_000, 0.01, 12, AmortizationSystem.PRICE, List.of()));
        assertThat(rows).hasSize(12);
        assertThat(rows).allSatisfy(r -> assertThat(r.payment()).isEqualByComparingTo("888.49"));
        assertThat(rows.getLast().balance()).isEqualByComparingTo("0");
        assertThat(rows.getLast().month()).isEqualTo(YearMonth.of(2026, 12));
    }

    @Test
    void sacLowersThePaymentEveryMonth() {
        Totals totals = FinancingMath.totals(FinancingMath.schedule(plan(12_000, 0.01, 12, AmortizationSystem.SAC, List.of())));
        assertThat(totals.firstPayment()).isEqualByComparingTo("1120");
        assertThat(totals.lastPayment()).isEqualByComparingTo("1010");
        assertThat(totals.totalInterest()).isEqualByComparingTo("780");
    }

    @Test
    void extraPaymentShortensTermOrLowersPayment() {
        Extra term = new Extra(START, 5_000, ExtraMode.TERM);
        List<Row> shorter = FinancingMath.schedule(plan(12_000, 0, 12, AmortizationSystem.SAC, List.of(term)));
        assertThat(shorter).hasSize(7);
        assertThat(shorter.get(1).payment()).isEqualByComparingTo("1000");

        Extra payment = new Extra(START, 5_000, ExtraMode.PAYMENT);
        List<Row> lower = FinancingMath.schedule(plan(12_000, 0, 12, AmortizationSystem.SAC, List.of(payment)));
        assertThat(lower).hasSize(12);
        assertThat(lower.get(1).payment()).isEqualByComparingTo("545.45");
    }

    @Test
    void cetMatchesTheRateWithoutFeesAndGrowsWithThem() {
        List<Row> rows = FinancingMath.schedule(plan(10_000, 0.01, 12, AmortizationSystem.PRICE, List.of()));
        assertThat(FinancingMath.cet(10_000, rows)).isCloseTo(0.01, within(0.0001));
        assertThat(FinancingMath.cet(9_500, rows)).isGreaterThan(0.018);
    }

    @Test
    void correctionRaisesTheBalanceBeforeInterest() {
        Plan plan = new Plan(1_000, 0, 1, AmortizationSystem.SAC, 0, 0, START, m -> 0.01, List.of());
        assertThat(FinancingMath.schedule(plan).getFirst().payment()).isEqualByComparingTo("1010");
    }

    @Test
    void firstPaymentFactorMatchesTheSchedule() {
        Plan plan = new Plan(80_000, 0.012, 48, AmortizationSystem.PRICE, 0.0003, 0, START, m -> 0d, List.of());
        BigDecimal first = FinancingMath.schedule(plan).getFirst().payment();
        assertThat(80_000 * FinancingMath.firstPaymentFactor(plan)).isCloseTo(first.doubleValue(), within(0.01));
    }

    private static Plan plan(double principal, double rate, int months, AmortizationSystem system, List<Extra> extras) {
        return new Plan(principal, rate, months, system, 0, 0, START, m -> 0d, extras);
    }
}
