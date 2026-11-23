package com.lifepedia.api.school;

import com.lifepedia.api.school.SchoolCourse.Category;
import com.lifepedia.api.school.SchoolCourse.Status;
import com.lifepedia.api.school.SchoolEnrollment.Scale;
import com.lifepedia.api.school.SchoolMath.Graded;
import com.lifepedia.api.school.SchoolMath.Progress;
import com.lifepedia.api.school.SchoolMath.Result;
import com.lifepedia.api.school.SchoolMath.Rule;
import com.lifepedia.api.school.SchoolMath.Taken;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolMathTest {

    private static final Rule UFRN = new Rule(Scale.NUMERIC, new BigDecimal("6"), new BigDecimal("5"), new BigDecimal("3"), 75);

    @Test
    void partialAverageAndRiskWhileUnitsAreMissing() {
        Result r = SchoolMath.result(UFRN, grades("8.5", null, null), null, null);
        assertThat(r.partial()).isEqualByComparingTo("8.50");
        assertThat(r.average()).isNull();
        assertThat(r.outcome()).isNull();
        assertThat(r.risk()).isFalse();
        assertThat(SchoolMath.result(UFRN, grades("4", "5", null), null, null).risk()).isTrue();
        assertThat(SchoolMath.result(UFRN, grades(null, null, null), null, null).partial()).isNull();
    }

    @Test
    void ufrnRulesWithRecoveryReplacingTheLowest() {
        assertThat(SchoolMath.result(UFRN, grades("7", "6", "5"), null, null).outcome()).isEqualTo(Status.APR);
        assertThat(SchoolMath.result(UFRN, grades("6", "5", "4.5"), null, null).outcome()).isEqualTo(Status.APRN);
        assertThat(SchoolMath.result(UFRN, grades("8", "7", "1"), null, null).outcome()).isEqualTo(Status.REC);
        Result recovered = SchoolMath.result(UFRN, grades("8", "7", "1"), new BigDecimal("6"), null);
        assertThat(recovered.average()).isEqualByComparingTo("7.00");
        assertThat(recovered.outcome()).isEqualTo(Status.APR);
        assertThat(SchoolMath.result(UFRN, grades("7", "6", "2"), new BigDecimal("2.5"), null).outcome()).isEqualTo(Status.REPN);
        assertThat(SchoolMath.result(UFRN, grades("3", "4", "2"), new BigDecimal("4"), null).outcome()).isEqualTo(Status.REP);
    }

    @Test
    void conceptScale() {
        Rule masters = new Rule(Scale.CONCEPT, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 75);
        assertThat(SchoolMath.result(masters, List.of(), null, "b").outcome()).isEqualTo(Status.APR);
        assertThat(SchoolMath.result(masters, List.of(), null, "D").risk()).isTrue();
        assertThat(SchoolMath.result(masters, List.of(), null, null).outcome()).isNull();
    }

    @Test
    void unitGradeFromWeightedAssessments() {
        List<BigDecimal> units = SchoolMath.units(grades("5", "9", null), List.of(
                new Graded(1, new BigDecimal("2"), new BigDecimal("10")),
                new Graded(1, BigDecimal.ONE, new BigDecimal("7")),
                new Graded(3, BigDecimal.ONE, null)));
        assertThat(units.get(0)).isEqualByComparingTo("9.00");
        assertThat(units.get(1)).isEqualByComparingTo("9");
        assertThat(units.get(2)).isNull();
    }

    @Test
    void indexPeriodsAndProgress() {
        List<Taken> taken = List.of(
                new Taken("2025.1", Category.MANDATORY, Status.APR, 60, new BigDecimal("8")),
                new Taken("2025.1", Category.MANDATORY, Status.REP, 30, new BigDecimal("2")),
                new Taken("2025.2", Category.OPTIONAL, Status.APR, 60, new BigDecimal("10")),
                new Taken(null, Category.MANDATORY, Status.CUMP, 90, null),
                new Taken("2026.1", Category.MANDATORY, Status.MATR, 60, null));
        assertThat(SchoolMath.index(taken)).isEqualByComparingTo("7.60");
        assertThat(SchoolMath.periods(taken)).extracting(SchoolMath.PeriodAverage::period).containsExactly("2025.1", "2025.2", "2026.1");
        assertThat(SchoolMath.periods(taken).get(0).average()).isEqualByComparingTo("6.00");
        Progress p = SchoolMath.progress(taken, 300, 120, 30, 10);
        assertThat(p.mandatoryDone()).isEqualTo(150);
        assertThat(p.optionalDone()).isEqualTo(60);
        assertThat(p.complementaryDone()).isEqualTo(10);
        assertThat(p.ongoing()).isEqualTo(60);
        assertThat(p.totalRequired()).isEqualTo(450);
        assertThat(p.totalDone()).isEqualTo(220);
        assertThat(p.termsLeft()).isEqualTo(4);
        assertThat(SchoolMath.progress(taken, null, null, null, 0).termsLeft()).isNull();
    }

    private static List<BigDecimal> grades(String... values) {
        return Arrays.stream(values).map(v -> v == null ? null : new BigDecimal(v)).toList();
    }
}
