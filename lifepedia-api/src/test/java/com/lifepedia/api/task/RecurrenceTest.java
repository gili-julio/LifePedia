package com.lifepedia.api.task;

import com.lifepedia.api.task.Recurrence.MonthMode;
import com.lifepedia.api.task.Recurrence.Unit;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceTest {

    private static List<LocalDate> dates(Recurrence rule, String from, String to) {
        return rule.between(LocalDate.parse(from), LocalDate.parse(to)).stream().map(Recurrence.Occurrence::date).toList();
    }

    private static Recurrence rule(String start, Unit unit, int interval) {
        return new Recurrence(LocalDate.parse(start), unit, interval, 0, null, false, null, null);
    }

    @Test
    void everyTwoDays() {
        assertThat(dates(rule("2026-01-01", Unit.DAY, 2), "2026-01-01", "2026-01-07"))
                .extracting(LocalDate::getDayOfMonth).containsExactly(1, 3, 5, 7);
    }

    @Test
    void weekdaysEveryOtherWeekStartingMidWeek() {
        int mask = Recurrence.bit(DayOfWeek.MONDAY) | Recurrence.bit(DayOfWeek.WEDNESDAY) | Recurrence.bit(DayOfWeek.FRIDAY);
        Recurrence rule = new Recurrence(LocalDate.parse("2026-01-07"), Unit.WEEK, 2, mask, null, false, null, null);
        assertThat(dates(rule, "2026-01-01", "2026-01-31"))
                .extracting(LocalDate::getDayOfMonth).containsExactly(7, 9, 19, 21, 23);
    }

    @Test
    void weeklyWithoutDaysUsesStartDay() {
        assertThat(dates(rule("2026-01-07", Unit.WEEK, 1), "2026-01-01", "2026-01-21"))
                .extracting(LocalDate::getDayOfMonth).containsExactly(7, 14, 21);
    }

    @Test
    void monthlyDay31ClampsToShortMonths() {
        assertThat(dates(rule("2026-01-31", Unit.MONTH, 1), "2026-01-01", "2026-04-30")).containsExactly(
                LocalDate.parse("2026-01-31"), LocalDate.parse("2026-02-28"),
                LocalDate.parse("2026-03-31"), LocalDate.parse("2026-04-30"));
    }

    @Test
    void monthlyLastDay() {
        Recurrence rule = new Recurrence(LocalDate.parse("2026-01-10"), Unit.MONTH, 1, 0, MonthMode.LAST_DAY, false, null, null);
        assertThat(dates(rule, "2026-01-01", "2026-03-31")).containsExactly(
                LocalDate.parse("2026-01-31"), LocalDate.parse("2026-02-28"), LocalDate.parse("2026-03-31"));
    }

    @Test
    void monthlyNthWeekdayAndFifthBecomesLast() {
        Recurrence second = new Recurrence(LocalDate.parse("2026-01-13"), Unit.MONTH, 1, 0, MonthMode.WEEKDAY, false, null, null);
        assertThat(dates(second, "2026-01-01", "2026-03-31")).containsExactly(
                LocalDate.parse("2026-01-13"), LocalDate.parse("2026-02-10"), LocalDate.parse("2026-03-10"));
        Recurrence fifth = new Recurrence(LocalDate.parse("2026-01-29"), Unit.MONTH, 1, 0, MonthMode.WEEKDAY, false, null, null);
        assertThat(dates(fifth, "2026-01-01", "2026-03-31")).containsExactly(
                LocalDate.parse("2026-01-29"), LocalDate.parse("2026-02-26"), LocalDate.parse("2026-03-26"));
    }

    @Test
    void yearlyOnLeapDay() {
        assertThat(dates(rule("2028-02-29", Unit.YEAR, 1), "2028-01-01", "2029-12-31"))
                .containsExactly(LocalDate.parse("2028-02-29"), LocalDate.parse("2029-02-28"));
    }

    @Test
    void countAndUntilEndTheSeries() {
        Recurrence count = new Recurrence(LocalDate.parse("2026-01-01"), Unit.DAY, 1, 0, null, false, null, 3);
        assertThat(dates(count, "2026-01-01", "2026-12-31")).hasSize(3);
        Recurrence until = new Recurrence(LocalDate.parse("2026-01-01"), Unit.DAY, 1, 0, null, false,
                LocalDate.parse("2026-01-05"), null);
        assertThat(dates(until, "2026-01-03", "2026-12-31")).extracting(LocalDate::getDayOfMonth).containsExactly(3, 4, 5);
    }

    @Test
    void indexesAndLookups() {
        Recurrence rule = rule("2026-01-01", Unit.DAY, 2);
        assertThat(rule.occursOn(LocalDate.parse("2026-01-05"))).isTrue();
        assertThat(rule.occursOn(LocalDate.parse("2026-01-04"))).isFalse();
        assertThat(rule.indexOf(LocalDate.parse("2026-01-05"))).isEqualTo(2);
        assertThat(rule.countBefore(LocalDate.parse("2026-01-05"))).isEqualTo(2);
        assertThat(rule.countBefore(LocalDate.parse("2026-01-01"))).isZero();
    }

    @Test
    void fromCompletionHasOnlyTheCurrentOccurrence() {
        Recurrence rule = new Recurrence(LocalDate.parse("2026-01-10"), Unit.DAY, 3, 0, null, true, null, null);
        assertThat(dates(rule, "2026-01-01", "2026-01-31")).containsExactly(LocalDate.parse("2026-01-10"));
        assertThat(rule.after(LocalDate.parse("2026-01-12"))).isEqualTo(LocalDate.parse("2026-01-15"));
    }
}
