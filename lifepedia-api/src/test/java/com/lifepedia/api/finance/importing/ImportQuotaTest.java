package com.lifepedia.api.finance.importing;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportQuotaTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void freeWithoutRecentSends() {
        ImportQuota quota = ImportQuota.of(List.of(NOW.minusHours(5)), NOW);
        assertThat(quota.nextAt()).isNull();
        assertThat(quota.usedWeek()).isEqualTo(1);
        assertThat(quota.usedMonth()).isEqualTo(1);
    }

    @Test
    void oneSendEveryFourHours() {
        assertThat(ImportQuota.of(List.of(NOW.minusHours(3)), NOW).nextAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    void fiveSendsPerWeekReleaseWhenTheFifthNewestLeaves() {
        List<OffsetDateTime> sent = List.of(NOW.minusDays(1), NOW.minusDays(2), NOW.minusDays(3), NOW.minusDays(4), NOW.minusDays(6));
        assertThat(ImportQuota.of(sent, NOW).nextAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    void tenSendsPerMonthReleaseWhenTheTenthNewestLeaves() {
        List<OffsetDateTime> sent = List.of(NOW.minusDays(8), NOW.minusDays(9), NOW.minusDays(10), NOW.minusDays(11), NOW.minusDays(12),
                NOW.minusDays(13), NOW.minusDays(14), NOW.minusDays(15), NOW.minusDays(16), NOW.minusDays(20));
        ImportQuota quota = ImportQuota.of(sent, NOW);
        assertThat(quota.nextAt()).isEqualTo(NOW.plusDays(10));
        assertThat(quota.usedWeek()).isZero();
        assertThat(quota.usedMonth()).isEqualTo(10);
    }

    @Test
    void waitsForTheLatestOfTheLimits() {
        List<OffsetDateTime> sent = List.of(NOW.minusHours(1), NOW.minusDays(2), NOW.minusDays(3), NOW.minusDays(4), NOW.minusDays(5));
        assertThat(ImportQuota.of(sent, NOW).nextAt()).isEqualTo(NOW.plusDays(2));
    }
}
