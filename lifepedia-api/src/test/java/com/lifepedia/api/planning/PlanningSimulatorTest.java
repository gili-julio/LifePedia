package com.lifepedia.api.planning;

import com.lifepedia.api.planning.PlanningSimulator.Entry;
import com.lifepedia.api.planning.PlanningSimulator.Result;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningSimulatorTest {

    private static final YearMonth START = YearMonth.of(2026, 1);
    private static final List<BigDecimal> BASE = Collections.nCopies(24, new BigDecimal("100.00"));

    @Test
    void priorityDecidesWhoGetsTheMoney() {
        UUID laptop = UUID.randomUUID();
        UUID trip = UUID.randomUUID();
        UUID gift = UUID.randomUUID();
        Result r = PlanningSimulator.run(START, BASE, BigDecimal.ZERO, List.of(
                new Entry(laptop, new BigDecimal("1000"), YearMonth.of(2026, 10), 1, true),
                new Entry(trip, new BigDecimal("500"), YearMonth.of(2026, 3), 1, true),
                new Entry(gift, new BigDecimal("200"), YearMonth.of(2026, 2), 1, false)));

        assertThat(r.items().get(laptop).shortfall()).isEqualByComparingTo("0");
        assertThat(r.items().get(laptop).earliest()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(r.items().get(laptop).monthlySaving()).isEqualByComparingTo("100");
        // Todo o dinheiro até outubro já é do notebook: a viagem só cabe em março/2027.
        assertThat(r.items().get(trip).shortfall()).isEqualByComparingTo("500");
        assertThat(r.items().get(trip).earliest()).isEqualTo(YearMonth.of(2027, 3));
        // Não considerado: quanto faltaria se entrasse por último.
        assertThat(r.items().get(gift).shortfall()).isEqualByComparingTo("200");

        assertThat(r.count()).isEqualTo(2);
        assertThat(r.total()).isEqualByComparingTo("1500");
        assertThat(r.shortfall()).isEqualByComparingTo("500");
        assertThat(r.lowestMonth()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(r.endMonth()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(r.timeline().get(9).planned()).isEqualByComparingTo("1000");
        assertThat(r.timeline().get(9).withoutPlan()).isEqualByComparingTo("1000");
        assertThat(r.timeline().get(9).due()).containsExactly(laptop);
    }

    @Test
    void installmentsAndReserve() {
        UUID car = UUID.randomUUID();
        Result r = PlanningSimulator.run(START, BASE, new BigDecimal("100"), List.of(
                new Entry(car, new BigDecimal("1300"), YearMonth.of(2025, 11), 12, true)));

        // Prazo passado conta no mês atual; parcela de 108,33 e a última com o resto.
        assertThat(r.items().get(car).overdue()).isTrue();
        assertThat(r.items().get(car).installment()).isEqualByComparingTo("108.33");
        assertThat(r.items().get(car).monthlySaving()).isNull();
        assertThat(r.items().get(car).shortfall()).isEqualByComparingTo("0");
        assertThat(r.timeline().get(11).planned()).isEqualByComparingTo("108.37");
        assertThat(r.endMonth()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(PlanningSimulator.installments(130000, 12)[11]).isEqualTo(10837);
    }

    @Test
    void earliestLooksBeyondTheShownPeriod() {
        UUID sofa = UUID.randomUUID();
        Result r = PlanningSimulator.run(START, Collections.nCopies(36, new BigDecimal("100")), BigDecimal.ZERO,
                List.of(new Entry(sofa, new BigDecimal("3000"), START, 1, true)), 12);
        assertThat(r.timeline()).hasSize(12);
        assertThat(r.items().get(sofa).shortfall()).isEqualByComparingTo("2900");
        assertThat(r.items().get(sofa).earliest()).isEqualTo(YearMonth.of(2028, 6));
    }

    @Test
    void horizonCoversLastPaymentWithLimits() {
        assertThat(PlanningSimulator.horizon(START, List.of())).isEqualTo(YearMonth.of(2027, 12));
        assertThat(PlanningSimulator.horizon(START, List.of(
                new Entry(UUID.randomUUID(), BigDecimal.TEN, YearMonth.of(2030, 1), 24, true))))
                .isEqualTo(YearMonth.of(2031, 12));
        assertThat(PlanningSimulator.horizon(START, List.of(
                new Entry(UUID.randomUUID(), BigDecimal.TEN, YearMonth.of(2045, 1), 120, true))))
                .isEqualTo(START.plusMonths(PlanningSimulator.MAX_MONTHS - 1L));
    }
}
