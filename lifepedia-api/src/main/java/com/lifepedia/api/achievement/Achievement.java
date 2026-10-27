package com.lifepedia.api.achievement;

import java.util.List;

/** Conquistas e os valores de cada nível (o texto fica no front). {@code ownerOnly}: amigos não veem. */
public enum Achievement {
    TASKS_DONE(false, List.of(1, 10, 50, 100, 250, 500, 1000)),
    GROUP_TASKS_DONE(false, List.of(1, 10, 50, 100, 250)),
    HABIT_STREAK(false, List.of(3, 7, 14, 30, 100, 365)),
    FRIENDS(false, List.of(1, 3, 10, 25)),
    GROUPS(false, List.of(1, 3, 5)),
    TRANSACTIONS(true, List.of(1, 50, 200, 500, 1000));

    private final boolean ownerOnly;
    private final List<Integer> levels;

    Achievement(boolean ownerOnly, List<Integer> levels) {
        this.ownerOnly = ownerOnly;
        this.levels = levels;
    }

    public boolean isOwnerOnly() {
        return ownerOnly;
    }

    public List<Integer> getLevels() {
        return levels;
    }

    public int maxLevel() {
        return levels.size();
    }

    /** Valor que dá o nível {@code level} (1 = primeiro). */
    public int threshold(int level) {
        return levels.get(level - 1);
    }

    /** Níveis que {@code value} alcança. */
    public int levelOf(long value) {
        return (int) levels.stream().filter(t -> value >= t).count();
    }
}
