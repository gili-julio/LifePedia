package com.lifepedia.api.attendance;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Compromisso recorrente (ex.: aula) com limite de faltas. */
@Entity
@Table(name = "commitments")
public class Commitment extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;

    /** 1 = segunda ... 7 = domingo, em ordem. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private int[] weekdays;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private short durationMinutes;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "max_absences", nullable = false)
    private short maxAbsences;

    @Column(name = "task_id")
    private UUID taskId;

    protected Commitment() {
    }

    public Commitment(UUID userId) {
        this.userId = userId;
    }

    public void update(String name, String color, List<Integer> weekdays, LocalTime startTime, int durationMinutes,
                       LocalDate startDate, LocalDate endDate, int maxAbsences, UUID taskId) {
        this.name = name;
        this.color = color;
        this.weekdays = weekdays.stream().mapToInt(Integer::intValue).distinct().sorted().toArray();
        this.startTime = startTime;
        this.durationMinutes = (short) durationMinutes;
        this.startDate = startDate;
        this.endDate = endDate;
        this.maxAbsences = (short) maxAbsences;
        this.taskId = taskId;
    }

    /** Acontece neste dia (dia da semana e dentro do período). */
    public boolean occursOn(LocalDate date) {
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate))
                && Arrays.stream(weekdays).anyMatch(d -> d == date.getDayOfWeek().getValue());
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public List<Integer> getWeekdays() {
        return Arrays.stream(weekdays).boxed().toList();
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getMaxAbsences() {
        return maxAbsences;
    }

    public UUID getTaskId() {
        return taskId;
    }
}
