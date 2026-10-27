package com.lifepedia.api.achievement;

import java.time.OffsetDateTime;

public final class AchievementDtos {

    private AchievementDtos() {
    }

    /**
     * @param value      valor atual (nunca abaixo da meta do nível já conquistado; no nível máximo deixa de ser contado)
     * @param level      níveis conquistados (0 = bloqueada); nunca diminui
     * @param target     valor do próximo nível (nulo = todos conquistados)
     * @param unlockedAt quando o nível atual foi conquistado (nulo = bloqueada)
     */
    public record AchievementResponse(Achievement code, long value, int level, int maxLevel, Integer target,
                                      OffsetDateTime unlockedAt, boolean ownerOnly) {

        /** Resposta a partir do nível gravado (puro, testado em {@code AchievementResponseTest}). */
        public static AchievementResponse of(Achievement code, long value, int level, OffsetDateTime unlockedAt) {
            long shown = level > 0 ? Math.max(value, code.threshold(level)) : value;
            Integer target = level < code.maxLevel() ? code.threshold(level + 1) : null;
            return new AchievementResponse(code, shown, level, code.maxLevel(), target, unlockedAt, code.isOwnerOnly());
        }
    }
}
