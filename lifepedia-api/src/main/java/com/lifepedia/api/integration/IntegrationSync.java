package com.lifepedia.api.integration;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Uma sincronização de conta externa: resultado, quantos itens vieram e o motivo da falha. */
@Entity
@Table(name = "integration_syncs")
public class IntegrationSync extends BaseEntity {

    public enum Outcome { SUCCESS, FAILED }

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Outcome outcome;

    @Column(nullable = false, updatable = false)
    private boolean manual;

    @Column(nullable = false, updatable = false)
    private int items;

    @Column(length = 300, updatable = false)
    private String message;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private long durationMs;

    protected IntegrationSync() {
    }

    public IntegrationSync(UUID accountId, Outcome outcome, boolean manual, int items, String message, long durationMs) {
        this.accountId = accountId;
        this.outcome = outcome;
        this.manual = manual;
        this.items = items;
        this.message = message == null || message.length() <= 300 ? message : message.substring(0, 300);
        this.durationMs = durationMs;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public boolean isManual() {
        return manual;
    }

    public int getItems() {
        return items;
    }

    public String getMessage() {
        return message;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
