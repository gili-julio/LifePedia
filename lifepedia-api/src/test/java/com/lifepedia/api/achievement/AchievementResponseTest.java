package com.lifepedia.api.achievement;

import com.lifepedia.api.achievement.AchievementDtos.AchievementResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementResponseTest {

    @Test
    void levelsReachedByValue() {
        assertThat(Achievement.GROUPS.levelOf(0)).isZero();
        assertThat(Achievement.GROUPS.levelOf(3)).isEqualTo(2);
        assertThat(Achievement.GROUPS.levelOf(9)).isEqualTo(Achievement.GROUPS.maxLevel());
    }

    @Test
    void nextTargetAndKeptLevel() {
        AchievementResponse none = AchievementResponse.of(Achievement.GROUPS, 0, 0, null);
        assertThat(none.target()).isEqualTo(1);
        assertThat(none.unlockedAt()).isNull();

        // Nível 2 já conquistado com o valor caindo para 1: continua no 2, sem barra abaixo da meta dele.
        OffsetDateTime at = OffsetDateTime.now();
        AchievementResponse kept = AchievementResponse.of(Achievement.GROUPS, 1, 2, at);
        assertThat(kept.level()).isEqualTo(2);
        assertThat(kept.value()).isEqualTo(3);
        assertThat(kept.target()).isEqualTo(5);
        assertThat(kept.unlockedAt()).isEqualTo(at);

        AchievementResponse max = AchievementResponse.of(Achievement.GROUPS, 5, 3, at);
        assertThat(max.target()).isNull();
    }
}
