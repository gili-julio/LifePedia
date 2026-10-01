package com.lifepedia.api.task;

import com.lifepedia.api.task.HabitStats.Day;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HabitStatsTest {

    private static final LocalDate START = LocalDate.parse("2026-09-01");

    /** Um dia por letra a partir de 01/09: D feita, S pulada, M perdida, P pendente. */
    private static HabitStats stats(String days, String monthStart) {
        List<LocalDate> dates = new ArrayList<>();
        Map<Character, Day> codes = Map.of('D', Day.DONE, 'S', Day.SKIPPED, 'M', Day.MISSED, 'P', Day.PENDING);
        for (int i = 0; i < days.length(); i++) dates.add(START.plusDays(i));
        return HabitStats.of(dates, d -> codes.get(days.charAt((int) (d.toEpochDay() - START.toEpochDay()))),
                LocalDate.parse(monthStart));
    }

    @Test
    void skippedIsNeutralAndTodayPendingDoesNotBreak() {
        HabitStats s = stats("DDSDP", "2026-09-01");
        assertThat(s.streak()).isEqualTo(3);
        assertThat(s.best()).isEqualTo(3);
        assertThat(s.monthRate()).isEqualTo(100);
    }

    @Test
    void missedBreaksStreakButBestRemains() {
        HabitStats s = stats("DDDDMDD", "2026-09-01");
        assertThat(s.streak()).isEqualTo(2);
        assertThat(s.best()).isEqualTo(4);
        assertThat(s.monthDone()).isEqualTo(6);
        assertThat(s.monthDue()).isEqualTo(7);
        assertThat(s.monthRate()).isEqualTo(86);
    }

    @Test
    void monthCountsOnlyFromMonthStart() {
        HabitStats s = stats("MMDD", "2026-09-03");
        assertThat(s.monthRate()).isEqualTo(100);
        assertThat(stats("P", "2026-09-01").monthRate()).isNull();
    }
}
