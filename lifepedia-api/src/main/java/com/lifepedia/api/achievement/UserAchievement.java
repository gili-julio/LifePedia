package com.lifepedia.api.achievement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Nível de conquista alcançado (gravado por {@link UserAchievementRepository#unlock}, nunca apagado). */
@Entity
@Table(name = "user_achievements")
public class UserAchievement {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private Achievement code;

    @Column(nullable = false, updatable = false)
    private int level;

    @Column(name = "unlocked_at", nullable = false, updatable = false)
    private OffsetDateTime unlockedAt;

    protected UserAchievement() {
    }

    public Achievement getCode() {
        return code;
    }

    public int getLevel() {
        return level;
    }

    public OffsetDateTime getUnlockedAt() {
        return unlockedAt;
    }
}
