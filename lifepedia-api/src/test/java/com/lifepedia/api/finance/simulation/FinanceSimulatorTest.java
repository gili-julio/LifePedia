package com.lifepedia.api.finance.simulation;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.simulation.FinanceSimulator.Invest;
import com.lifepedia.api.finance.simulation.FinanceSimulator.Market;
import com.lifepedia.api.finance.simulation.FinanceSimulator.Outcome;
import com.lifepedia.api.finance.simulation.FinanceSimulator.Plan;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Change;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Yield;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FinanceSimulatorTest {

    private static final YearMonth START = YearMonth.of(2026, 1);
    private static final Market MARKET = new Market(0.01, 0.0095, 0.004, 0.001);

    @Test
    void baseAndChangesAccumulate() {
        List<Change> changes = List.of(
                new Change("Bônus", EntryType.INCOME, new BigDecimal("500"), START.plusMonths(1), null, true),
                new Change("Academia", EntryType.EXPENSE, new BigDecimal("100"), START.plusMonths(2), START.plusMonths(3), false));
        Outcome out = FinanceSimulator.run(new Plan(START, 1000, new double[]{100, 100, 100, 100, 100}, changes, null, 0, false));

        assertThat(out.points()).extracting(p -> p.current().doubleValue()).containsExactly(1100.0, 1200.0, 1300.0, 1400.0, 1500.0);
        assertThat(out.points()).extracting(p -> p.simulated().doubleValue()).containsExactly(1100.0, 1700.0, 1700.0, 1700.0, 1800.0);
        assertThat(out.summary().difference().doubleValue()).isEqualTo(300.0);
        assertThat(out.summary().monthlyRate()).isNull();
    }

    @Test
    void marksTheFirstNegativeMonth() {
        Outcome out = FinanceSimulator.run(new Plan(START, 100, new double[]{-60, -60, -60}, List.of(), null, 0, false));
        assertThat(out.summary().firstNegativeCurrent()).isEqualTo(START.plusMonths(1));
        assertThat(out.summary().firstNegativeSimulated()).isEqualTo(START.plusMonths(1));
    }

    @Test
    void investmentCompoundsAndLeavesTheBalance() {
        Invest invest = new Invest(1000, 100, 0.01, false, true);
        Outcome out = FinanceSimulator.run(new Plan(START, 0, new double[]{500, 500}, List.of(), invest, 0, false));

        double gross = 1000 * 1.01 * 1.01 + 100 * 1.01 * 1.01 + 100 * 1.01;
        assertThat(out.summary().contributed().doubleValue()).isEqualTo(1200.0);
        assertThat(out.points().getLast().cash().doubleValue()).isEqualTo(800.0);
        assertThat(out.summary().finalSimulated().doubleValue()).isCloseTo(800 + gross, within(0.01));
        assertThat(out.summary().interest().doubleValue()).isCloseTo(gross - 1200, within(0.01));
    }

    @Test
    void incomeTaxFollowsTheAgeOfEachContribution() {
        assertThat(FinanceSimulator.taxRate(6)).isEqualTo(0.225);
        assertThat(FinanceSimulator.taxRate(7)).isEqualTo(0.20);
        assertThat(FinanceSimulator.taxRate(24)).isEqualTo(0.175);
        assertThat(FinanceSimulator.taxRate(25)).isEqualTo(0.15);

        Invest invest = new Invest(1000, 0, 0.01, true, true);
        Outcome out = FinanceSimulator.run(new Plan(START, 0, new double[3], List.of(), invest, 0, false));
        double gain = 1000 * Math.pow(1.01, 3) - 1000;
        assertThat(out.summary().tax().doubleValue()).isCloseTo(gain * 0.225, within(0.01));
        assertThat(out.summary().invested().doubleValue()).isCloseTo(1000 + gain * 0.775, within(0.01));
    }

    @Test
    void realValuesDiscountInflation() {
        Outcome out = FinanceSimulator.run(new Plan(START, 1000, new double[]{0, 0}, List.of(), null, 0.01, true));
        assertThat(out.summary().finalCurrent().doubleValue()).isCloseTo(1000 / 1.0201, within(0.01));
    }

    @Test
    void ratesBySource() {
        assertThat(FinanceSimulator.monthlyRate(Yield.CDI, 110, MARKET)).isCloseTo(0.011, within(1e-9));
        assertThat(FinanceSimulator.monthlyRate(Yield.SELIC, 100, MARKET)).isCloseTo(0.0095, within(1e-9));
        assertThat(FinanceSimulator.monthlyRate(Yield.FIXED, 12, MARKET)).isCloseTo(Math.pow(1.12, 1d / 12) - 1, within(1e-9));
        assertThat(FinanceSimulator.monthlyRate(Yield.IPCA, 6, MARKET))
                .isCloseTo(1.004 * Math.pow(1.06, 1d / 12) - 1, within(1e-9));
        // Selic acima de 8,5% a.a.: poupança rende 0,5% + TR.
        assertThat(FinanceSimulator.monthlyRate(Yield.SAVINGS, 0, MARKET)).isCloseTo(0.006, within(1e-9));
        Market low = new Market(0.005, 0.005, 0.004, 0);
        double selicYear = Math.pow(1.005, 12) - 1;
        assertThat(FinanceSimulator.monthlyRate(Yield.SAVINGS, 0, low)).isCloseTo(Math.pow(1 + selicYear * 0.7, 1d / 12) - 1, within(1e-9));
    }
}
