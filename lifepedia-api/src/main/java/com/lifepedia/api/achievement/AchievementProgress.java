package com.lifepedia.api.achievement;

import java.util.Set;
import java.util.UUID;

/** Ação que pode subir o nível de {@code codes} de {@code userId}; conferido depois do commit. */
public record AchievementProgress(UUID userId, Set<Achievement> codes) {
}
