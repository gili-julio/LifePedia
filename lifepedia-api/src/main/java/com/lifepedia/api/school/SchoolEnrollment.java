package com.lifepedia.api.school;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Vínculo com um curso (graduação, mestrado...) com a regra de aprovação e a carga horária exigida. */
@Entity
@Table(name = "school_enrollments")
public class SchoolEnrollment extends BaseEntity {

    public enum Kind { UNDERGRADUATE, SECOND_DEGREE, MASTERS, DOCTORATE, SPECIALIZATION, TECHNICAL, OTHER }

    /** Nota de 0 a 10 ou conceito (A, B, C...). */
    public enum Scale { NUMERIC, CONCEPT }

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(nullable = false, length = 160)
    private String course;

    @Column(length = 160)
    private String institution;

    @Column(length = 40)
    private String registration;

    @Column(name = "start_period", length = 7)
    private String startPeriod;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, length = 7)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Scale scale;

    @Column(name = "pass_average", nullable = false, precision = 4, scale = 2)
    private BigDecimal passAverage;

    @Column(name = "low_average", nullable = false, precision = 4, scale = 2)
    private BigDecimal lowAverage;

    @Column(name = "min_unit", nullable = false, precision = 4, scale = 2)
    private BigDecimal minUnit;

    @Column(name = "min_attendance", nullable = false)
    private int minAttendance;

    @Column(name = "required_mandatory")
    private Integer requiredMandatory;

    @Column(name = "required_optional")
    private Integer requiredOptional;

    @Column(name = "required_complementary")
    private Integer requiredComplementary;

    @Column(name = "complementary_done", nullable = false)
    private int complementaryDone;

    @Column(nullable = false)
    private int position;

    protected SchoolEnrollment() {
    }

    public SchoolEnrollment(UUID userId, int position) {
        this.userId = userId;
        this.position = position;
    }

    public void set(Kind kind, String course, String institution, String registration, String startPeriod, boolean active,
                    String color) {
        this.kind = kind;
        this.course = course;
        this.institution = institution;
        this.registration = registration;
        this.startPeriod = startPeriod;
        this.active = active;
        this.color = color;
    }

    public void setRules(Scale scale, BigDecimal passAverage, BigDecimal lowAverage, BigDecimal minUnit, int minAttendance) {
        this.scale = scale;
        this.passAverage = passAverage;
        this.lowAverage = lowAverage;
        this.minUnit = minUnit;
        this.minAttendance = minAttendance;
    }

    public void setRequired(Integer mandatory, Integer optional, Integer complementary) {
        this.requiredMandatory = mandatory;
        this.requiredOptional = optional;
        this.requiredComplementary = complementary;
    }

    public void setComplementaryDone(int complementaryDone) {
        this.complementaryDone = complementaryDone;
    }

    public UUID getUserId() {
        return userId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getCourse() {
        return course;
    }

    public String getInstitution() {
        return institution;
    }

    public String getRegistration() {
        return registration;
    }

    public String getStartPeriod() {
        return startPeriod;
    }

    public boolean isActive() {
        return active;
    }

    public String getColor() {
        return color;
    }

    public Scale getScale() {
        return scale;
    }

    public BigDecimal getPassAverage() {
        return passAverage;
    }

    public BigDecimal getLowAverage() {
        return lowAverage;
    }

    public BigDecimal getMinUnit() {
        return minUnit;
    }

    public int getMinAttendance() {
        return minAttendance;
    }

    public Integer getRequiredMandatory() {
        return requiredMandatory;
    }

    public Integer getRequiredOptional() {
        return requiredOptional;
    }

    public Integer getRequiredComplementary() {
        return requiredComplementary;
    }

    public int getComplementaryDone() {
        return complementaryDone;
    }

    public int getPosition() {
        return position;
    }
}
