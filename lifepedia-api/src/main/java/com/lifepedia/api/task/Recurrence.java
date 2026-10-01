package com.lifepedia.api.task;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Regra de repetição de uma tarefa: gera os dias das ocorrências (puro, testado em {@code RecurrenceTest}).
 *
 * @param weekdays bits seg=1 ... dom=64 (só {@code WEEK}; 0 = dia da semana do início)
 * @param fromCompletion a próxima conta a partir de quando concluiu: só existe a ocorrência em {@code start}
 */
public record Recurrence(LocalDate start, Unit unit, int interval, int weekdays, MonthMode monthMode,
                         boolean fromCompletion, LocalDate until, Integer count) {

    public enum Unit { DAY, WEEK, MONTH, YEAR }

    /** Mensal: mesmo dia do mês, último dia, ou mesma semana/dia (ex.: 2ª terça; 5ª vira "última"). */
    public enum MonthMode { DAY_OF_MONTH, LAST_DAY, WEEKDAY }

    /** Trava contra regra que nunca chega ao fim do intervalo pedido. */
    private static final int MAX_STEPS = 200_000;

    public Recurrence {
        if (unit == Unit.WEEK && weekdays == 0) weekdays = bit(start.getDayOfWeek());
        if (unit == Unit.MONTH && monthMode == null) monthMode = MonthMode.DAY_OF_MONTH;
    }

    public static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }

    /** Ocorrência com o índice na série (0 = primeira). */
    public record Occurrence(LocalDate date, int index) {
    }

    /** Ocorrências de {@code from} a {@code to} (inclusive), em ordem. */
    public List<Occurrence> between(LocalDate from, LocalDate to) {
        List<Occurrence> result = new ArrayList<>();
        if (fromCompletion) {
            if (!start.isBefore(from) && !start.isAfter(to)) result.add(new Occurrence(start, 0));
            return result;
        }
        walk(to, (date, index) -> {
            if (!date.isBefore(from)) result.add(new Occurrence(date, index));
        });
        return result;
    }

    public boolean occursOn(LocalDate date) {
        return !between(date, date).isEmpty();
    }

    /** Índice da ocorrência do dia; -1 se a regra não cai nele. */
    public int indexOf(LocalDate date) {
        List<Occurrence> found = between(date, date);
        return found.isEmpty() ? -1 : found.getFirst().index();
    }

    /** Quantas ocorrências vêm antes do dia. */
    public int countBefore(LocalDate date) {
        if (!date.isAfter(start)) return 0;
        return between(start, date.minusDays(1)).size();
    }

    /** Próximo dia depois de {@code day} somando o intervalo (usado em "a partir da conclusão"). */
    public LocalDate after(LocalDate day) {
        return switch (unit) {
            case DAY -> day.plusDays(interval);
            case WEEK -> day.plusWeeks(interval);
            case MONTH -> day.plusMonths(interval);
            case YEAR -> day.plusYears(interval);
        };
    }

    /** A série ainda aceita a ocorrência de número {@code index} no dia {@code date}? */
    public boolean allows(LocalDate date, int index) {
        return (until == null || !date.isAfter(until)) && (count == null || index < count);
    }

    private interface Visitor {
        void visit(LocalDate date, int index);
    }

    private void walk(LocalDate to, Visitor visitor) {
        int index = 0;
        for (int step = 0; step < MAX_STEPS; step++) {
            List<LocalDate> period = period(step);
            for (LocalDate date : period) {
                if (date.isBefore(start)) continue;
                if (date.isAfter(to) || !allows(date, index)) return;
                visitor.visit(date, index++);
            }
        }
    }

    /** Dias do período {@code step} (em ordem). */
    private List<LocalDate> period(int step) {
        long k = (long) step * interval;
        return switch (unit) {
            case DAY -> List.of(start.plusDays(k));
            case YEAR -> List.of(start.plusYears(k));
            case WEEK -> {
                LocalDate monday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(k);
                List<LocalDate> days = new ArrayList<>(7);
                for (DayOfWeek day : DayOfWeek.values()) {
                    if ((weekdays & bit(day)) != 0) days.add(monday.plusDays(day.getValue() - 1L));
                }
                yield days;
            }
            case MONTH -> List.of(dayIn(YearMonth.from(start).plusMonths(k)));
        };
    }

    private LocalDate dayIn(YearMonth month) {
        return switch (monthMode) {
            case DAY_OF_MONTH -> month.atDay(Math.min(start.getDayOfMonth(), month.lengthOfMonth()));
            case LAST_DAY -> month.atEndOfMonth();
            case WEEKDAY -> {
                int nth = (start.getDayOfMonth() - 1) / 7 + 1;
                yield nth >= 5
                        ? month.atEndOfMonth().with(TemporalAdjusters.previousOrSame(start.getDayOfWeek()))
                        : month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(nth, start.getDayOfWeek()));
            }
        };
    }
}
