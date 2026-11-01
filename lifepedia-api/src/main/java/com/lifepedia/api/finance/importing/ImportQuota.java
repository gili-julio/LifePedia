package com.lifepedia.api.finance.importing;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

/**
 * Limites de envio à IA por conta, em janelas móveis.
 *
 * @param nextAt quando o próximo envio libera; nulo = pode enviar agora
 */
public record ImportQuota(int intervalHours, int weekLimit, int monthLimit, int usedWeek, int usedMonth, OffsetDateTime nextAt) {

    static final int INTERVAL_HOURS = 4;
    static final int WEEK_LIMIT = 5;
    static final int MONTH_LIMIT = 10;
    static final Duration MONTH = Duration.ofDays(30);

    private static final Duration WEEK = Duration.ofDays(7);
    private static final Duration INTERVAL = Duration.ofHours(INTERVAL_HOURS);

    /** @param sent envios dos últimos 30 dias, do mais novo para o mais antigo */
    static ImportQuota of(List<OffsetDateTime> sent, OffsetDateTime now) {
        OffsetDateTime next = Stream.of(released(sent, 1, INTERVAL, now), released(sent, WEEK_LIMIT, WEEK, now),
                        released(sent, MONTH_LIMIT, MONTH, now))
                .filter(time -> time != null)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        return new ImportQuota(INTERVAL_HOURS, WEEK_LIMIT, MONTH_LIMIT, within(sent, WEEK, now).size(),
                within(sent, MONTH, now).size(), next);
    }

    private static OffsetDateTime released(List<OffsetDateTime> sent, int limit, Duration window, OffsetDateTime now) {
        List<OffsetDateTime> inside = within(sent, window, now);
        return inside.size() < limit ? null : inside.get(limit - 1).plus(window);
    }

    private static List<OffsetDateTime> within(List<OffsetDateTime> sent, Duration window, OffsetDateTime now) {
        return sent.stream().filter(time -> time.isAfter(now.minus(window))).toList();
    }
}
