package com.lifepedia.api.school;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Prova ou trabalho de uma matéria; com nota, entra na média da unidade. */
@Entity
@Table(name = "school_assessments")
public class SchoolAssessment extends BaseEntity {

    public enum Kind { EXAM, ASSIGNMENT, PRESENTATION, QUIZ, OTHER }

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Kind kind;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    private Integer unit;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(precision = 4, scale = 2)
    private BigDecimal grade;

    @Column(name = "task_id")
    private UUID taskId;

    protected SchoolAssessment() {
    }

    public SchoolAssessment(UUID courseId, UUID userId) {
        this.courseId = courseId;
        this.userId = userId;
    }

    public void set(String title, Kind kind, LocalDate dueDate, LocalTime dueTime, Integer unit, BigDecimal weight, BigDecimal grade) {
        this.title = title;
        this.kind = kind;
        this.dueDate = dueDate;
        this.dueTime = dueDate == null ? null : dueTime;
        this.unit = unit;
        this.weight = weight;
        this.grade = grade;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public Kind getKind() {
        return kind;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public Integer getUnit() {
        return unit;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public BigDecimal getGrade() {
        return grade;
    }

    public UUID getTaskId() {
        return taskId;
    }
}
