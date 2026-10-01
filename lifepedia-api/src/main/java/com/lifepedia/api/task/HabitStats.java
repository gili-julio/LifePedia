package com.lifepedia.api.task;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * Sequência e cumprimento de um hábito (puro, testado em {@code HabitStatsTest}). Pulada é neutra; só a pendente de
 * dia que já passou quebra a sequência.
 *
 * @param streak    concluídas seguidas até hoje (a de hoje ainda pendente não quebra)
 * @param best      maior sequência no período olhado
 * @param monthDone concluídas no mês até hoje
 * @param monthDue  concluídas + perdidas no mês até hoje (0 = sem % ainda)
 */
public record HabitStats(int streak, int best, int monthDone, int monthDue) {

    /** Dias olhados para trás (a sequência não passa disso). */
    static final int LOOKBACK_DAYS = 400;

    /** Situação de uma ocorrência: {@code PENDING} = ainda dá tempo (hoje ou adiada para frente). */
    public enum Day { DONE, SKIPPED, MISSED, PENDING }

    /** @param dates dias das ocorrências até hoje, em ordem */
    public static HabitStats of(List<LocalDate> dates, Function<LocalDate, Day> state, LocalDate monthStart) {
        int run = 0, best = 0, done = 0, due = 0;
        for (LocalDate date : dates) {
            Day day = state.apply(date);
            if (day == Day.DONE) best = Math.max(best, ++run);
            else if (day == Day.MISSED) run = 0;
            if (!date.isBefore(monthStart)) {
                if (day == Day.DONE) done++;
                if (day == Day.DONE || day == Day.MISSED) due++;
            }
        }
        return new HabitStats(run, best, done, due);
    }

    /** % do mês (nulo sem ocorrência vencida). */
    public Integer monthRate() {
        return monthDue == 0 ? null : Math.round(monthDone * 100f / monthDue);
    }
}
