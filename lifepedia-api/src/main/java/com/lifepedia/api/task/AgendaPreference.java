package com.lifepedia.api.task;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Horas visíveis na agenda de uma pessoa ({@code dayEnd} exclusivo). */
@Entity
@Table(name = "agenda_settings")
public class AgendaPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "day_start", nullable = false)
    private short dayStart;

    @Column(name = "day_end", nullable = false)
    private short dayEnd;

    protected AgendaPreference() {
    }

    public AgendaPreference(UUID userId) {
        this.userId = userId;
    }

    public void set(int dayStart, int dayEnd) {
        this.dayStart = (short) dayStart;
        this.dayEnd = (short) dayEnd;
    }

    public int getDayStart() {
        return dayStart;
    }

    public int getDayEnd() {
        return dayEnd;
    }
}
