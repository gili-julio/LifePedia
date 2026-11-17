package com.lifepedia.api.vehicle;

import com.lifepedia.api.vehicle.VehicleMath.Consumption;
import com.lifepedia.api.vehicle.VehicleMath.Due;
import com.lifepedia.api.vehicle.VehicleMath.Fill;
import com.lifepedia.api.vehicle.VehicleMath.Reading;
import com.lifepedia.api.vehicle.VehicleMath.Stage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VehicleMathTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    @Test
    void paceUsesTheLastSixMonthsAndProjectsUpToNinetyDays() {
        List<Reading> readings = List.of(new Reading(TODAY.minusDays(400), 0), new Reading(TODAY.minusDays(100), 1_000),
                new Reading(TODAY.minusDays(10), 4_000));
        Double perDay = VehicleMath.kmPerDay(readings);
        assertThat(perDay).isCloseTo(3_000 / 90.0, within(0.001));
        assertThat(VehicleMath.estimatedKm(4_000, TODAY.minusDays(10), perDay, TODAY)).isEqualTo(4_333);
        assertThat(VehicleMath.estimatedKm(4_000, TODAY.minusDays(300), perDay, TODAY)).isEqualTo(7_000);
        assertThat(VehicleMath.kmPerDay(List.of(new Reading(TODAY.minusDays(3), 10), new Reading(TODAY, 50)))).isNull();
        assertThat(VehicleMath.estimatedKm(500, TODAY, null, TODAY)).isEqualTo(500);
    }

    @Test
    void dueByKmOrDateWhicheverComesFirst() {
        Due byKm = VehicleMath.item(1_000, 12, 9_000, TODAY.minusMonths(1), 9_950, 10.0, TODAY);
        assertThat(byKm.stage()).isEqualTo(Stage.SOON);
        assertThat(byKm.kmLeft()).isEqualTo(50);
        assertThat(byKm.expectedOn()).isEqualTo(TODAY.plusDays(5));

        Due byDate = VehicleMath.item(10_000, 6, 9_000, TODAY.minusMonths(6), 9_100, 1.0, TODAY);
        assertThat(byDate.stage()).isEqualTo(Stage.DUE);
        assertThat(byDate.expectedOn()).isEqualTo(TODAY);

        Due ok = VehicleMath.item(5_000, null, 9_000, null, 9_100, 10.0, TODAY);
        assertThat(ok.stage()).isEqualTo(Stage.OK);
        assertThat(ok.dueOn()).isNull();
        assertThat(VehicleMath.item(5_000, 12, null, null, 9_100, 10.0, TODAY)).isNull();

        assertThat(VehicleMath.document(TODAY.plusDays(20), TODAY).stage()).isEqualTo(Stage.SOON);
        assertThat(VehicleMath.document(TODAY, TODAY).stage()).isEqualTo(Stage.DUE);
        assertThat(VehicleMath.revision(20_000, null, 19_000, null, TODAY).stage()).isEqualTo(Stage.OK);
    }

    @Test
    void consumptionBetweenFullTanksSumsThePartialOnes() {
        List<Consumption> points = VehicleMath.consumption(List.of(
                new Fill(TODAY.minusDays(30), 1_000, new BigDecimal("40"), true),
                new Fill(TODAY.minusDays(20), 1_200, new BigDecimal("10"), false),
                new Fill(TODAY.minusDays(10), 1_500, new BigDecimal("30"), true),
                new Fill(TODAY.minusDays(5), 1_700, null, true),
                new Fill(TODAY.minusDays(4), 1_800, new BigDecimal("8"), true),
                new Fill(TODAY.minusDays(1), 1_900, new BigDecimal("10"), true)));
        assertThat(points).hasSize(2);
        assertThat(points.getFirst().kmPerLiter()).isEqualByComparingTo("12.50");
        assertThat(points.getFirst().km()).isEqualTo(500);
        assertThat(points.get(1).kmPerLiter()).isEqualByComparingTo("10.00");
    }

    @Test
    void kmDrivenStartsFromTheLastReadingBeforeThePeriod() {
        List<Reading> readings = List.of(new Reading(TODAY.minusDays(40), 1_000), new Reading(TODAY.minusDays(20), 1_300),
                new Reading(TODAY.minusDays(2), 1_800));
        assertThat(VehicleMath.kmDriven(readings, TODAY.minusDays(30), TODAY)).isEqualTo(800);
        assertThat(VehicleMath.kmDriven(readings, TODAY.minusDays(1), TODAY)).isNull();
    }
}
