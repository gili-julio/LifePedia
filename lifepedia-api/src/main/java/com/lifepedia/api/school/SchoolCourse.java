package com.lifepedia.api.school;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Matéria de um semestre do vínculo, com notas das unidades (JSON), links de acesso rápido e ligações com outros módulos. */
@Entity
@Table(name = "school_courses")
public class SchoolCourse extends BaseEntity {

    /** Obrigatória, optativa, eletiva ou complementar (soma em cada parte da carga horária). */
    public enum Category { MANDATORY, OPTIONAL, ELECTIVE, COMPLEMENTARY }

    /** Situação como no SIGAA. */
    public enum Status {
        MATR, APR, APRN, REC, REP, REPF, REPMF, REPN, REPNF, CANC, DISP, TRANC, TRANS, INCORP, CUMP;

        /** Conta como cumprida na carga horária. */
        public boolean passed() {
            return this == APR || this == APRN || this == DISP || this == TRANS || this == INCORP || this == CUMP;
        }

        /** Tem média final que entra no índice. */
        public boolean graded() {
            return this == APR || this == APRN || this == REP || this == REPF || this == REPMF || this == REPN || this == REPNF;
        }

        /** Ainda em andamento (notas parciais). */
        public boolean ongoing() {
            return this == MATR || this == REC;
        }
    }

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(length = 7)
    private String period;

    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    private Integer hours;

    @Column(name = "class_code", length = 20)
    private String classCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Status status;

    @Column(precision = 5, scale = 2)
    private BigDecimal attendance;

    @Column(precision = 4, scale = 2)
    private BigDecimal average;

    @Column(length = 2)
    private String concept;

    @Column(nullable = false, length = 2000)
    private String units;

    @Column(precision = 4, scale = 2)
    private BigDecimal recovery;

    private Integer absences;

    @Column(length = 160)
    private String teacher;

    @Column(name = "teacher_email", length = 160)
    private String teacherEmail;

    @Column(length = 80)
    private String room;

    @Column(length = 80)
    private String schedule;

    @Column(nullable = false, length = 8000)
    private String links;

    @Column(name = "commitment_id")
    private UUID commitmentId;

    @Column(name = "note_folder_id")
    private UUID noteFolderId;

    @Column(name = "group_id")
    private UUID groupId;

    /** Avisos de risco já mandados ("GRADE", "ABSENCES"), separados por vírgula. */
    @Column(name = "risk_warned", length = 20)
    private String riskWarned;

    protected SchoolCourse() {
    }

    public SchoolCourse(UUID userId, UUID enrollmentId) {
        this.userId = userId;
        this.enrollmentId = enrollmentId;
        this.units = "[]";
        this.links = "[]";
    }

    public void setBasics(String period, String code, String name, Integer hours, String classCode, Category category, Status status) {
        this.period = period;
        this.code = code;
        this.name = name;
        this.hours = hours;
        this.classCode = classCode;
        this.category = category;
        this.status = status;
    }

    public void setInfo(String teacher, String teacherEmail, String room, String schedule, String links) {
        this.teacher = teacher;
        this.teacherEmail = teacherEmail;
        this.room = room;
        this.schedule = schedule;
        this.links = links;
    }

    public void setFinal(BigDecimal attendance, BigDecimal average, String concept) {
        this.attendance = attendance;
        this.average = average;
        this.concept = concept;
    }

    public void setGrades(String units, BigDecimal recovery, Integer absences) {
        this.units = units;
        this.recovery = recovery;
        this.absences = absences;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setTeacher(String teacher) {
        this.teacher = teacher;
    }

    public void setClassCode(String classCode) {
        this.classCode = classCode;
    }

    public void setCommitmentId(UUID commitmentId) {
        this.commitmentId = commitmentId;
    }

    public void setNoteFolderId(UUID noteFolderId) {
        this.noteFolderId = noteFolderId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public void setRiskWarned(String riskWarned) {
        this.riskWarned = riskWarned;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getEnrollmentId() {
        return enrollmentId;
    }

    public String getPeriod() {
        return period;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getHours() {
        return hours;
    }

    public String getClassCode() {
        return classCode;
    }

    public Category getCategory() {
        return category;
    }

    public Status getStatus() {
        return status;
    }

    public BigDecimal getAttendance() {
        return attendance;
    }

    public BigDecimal getAverage() {
        return average;
    }

    public String getConcept() {
        return concept;
    }

    public String getUnits() {
        return units;
    }

    public BigDecimal getRecovery() {
        return recovery;
    }

    public Integer getAbsences() {
        return absences;
    }

    public String getTeacher() {
        return teacher;
    }

    public String getTeacherEmail() {
        return teacherEmail;
    }

    public String getRoom() {
        return room;
    }

    public String getSchedule() {
        return schedule;
    }

    public String getLinks() {
        return links;
    }

    public UUID getCommitmentId() {
        return commitmentId;
    }

    public UUID getNoteFolderId() {
        return noteFolderId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getRiskWarned() {
        return riskWarned;
    }
}
