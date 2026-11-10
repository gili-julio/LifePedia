package com.lifepedia.api.shared.ai;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Uma chamada à IA: só o resultado, nunca o documento nem a resposta. */
@Entity
@Table(name = "ai_usage")
public class AiUsage extends BaseEntity {

    /** Leitura ok ou o motivo da falha; os de {@link GeminiClient.Failure} têm o mesmo nome. */
    public enum Outcome { SUCCESS, EMPTY, TOO_MANY, TIMEOUT, BUSY, REJECTED, BLOCKED, TOO_LONG, FAILED, ERROR, STOPPED }

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 20, updatable = false)
    private String feature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Outcome outcome;

    @Column(name = "file_count", nullable = false, updatable = false)
    private int fileCount;

    @Column(name = "item_count", nullable = false, updatable = false)
    private int itemCount;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private long durationMs;

    protected AiUsage() {
    }

    public AiUsage(UUID userId, String feature, Outcome outcome, int fileCount, int itemCount, long durationMs) {
        this.userId = userId;
        this.feature = feature;
        this.outcome = outcome;
        this.fileCount = fileCount;
        this.itemCount = itemCount;
        this.durationMs = durationMs;
    }
}
