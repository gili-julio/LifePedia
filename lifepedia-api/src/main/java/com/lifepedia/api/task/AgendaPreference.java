package com.lifepedia.api.task;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** Horas visíveis na agenda de uma pessoa ({@code dayEnd} exclusivo) e o sono de cada dia da semana. */
@Entity
@Table(name = "agenda_settings")
public class AgendaPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "day_start", nullable = false)
    private short dayStart;

    @Column(name = "day_end", nullable = false)
    private short dayEnd;

    /** Minutos de sono de segunda a domingo. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "sleep_minutes", nullable = false)
    private int[] sleepMinutes;

    protected AgendaPreference() {
    }

    public AgendaPreference(UUID userId) {
        this.userId = userId;
    }

    public void set(int dayStart, int dayEnd, int[] sleepMinutes) {
        this.dayStart = (short) dayStart;
        this.dayEnd = (short) dayEnd;
        this.sleepMinutes = sleepMinutes.clone();
    }

    public int getDayStart() {
        return dayStart;
    }

    public int getDayEnd() {
        return dayEnd;
    }

    public int[] getSleepMinutes() {
        return sleepMinutes.clone();
    }
}
