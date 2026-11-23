package com.lifepedia.api.school;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lifepedia.api.school.SchoolCourse.Category;
import com.lifepedia.api.school.SchoolCourse.Status;
import com.lifepedia.api.school.SchoolEnrollment.Kind;
import com.lifepedia.api.school.SchoolEnrollment.Scale;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class SchoolDtos {

    static final String PERIOD = "^\\d{4}\\.[1-4]$";
    static final String PERIOD_MESSAGE = "Semestre no formato 2026.1.";

    private SchoolDtos() {
    }

    public enum LinkKind { DISCORD, WHATSAPP, TELEGRAM, CLASSROOM, DRIVE, MEET, SITE, OTHER }

    /** Regra de aprovação: média para aprovar, faixa com nota mínima por unidade e frequência mínima (%). */
    public record Rules(
            @NotNull(message = "Escolha nota ou conceito.") Scale scale,
            @NotNull(message = "Informe a média para aprovar.") @DecimalMin("0") @DecimalMax("10") BigDecimal passAverage,
            @NotNull(message = "Informe a média mínima.") @DecimalMin("0") @DecimalMax("10") BigDecimal lowAverage,
            @NotNull(message = "Informe a nota mínima por unidade.") @DecimalMin("0") @DecimalMax("10") BigDecimal minUnit,
            @NotNull(message = "Informe a frequência mínima.") @Min(0) @Max(100) Integer minAttendance) {
    }

    /** Carga horária exigida pelo curso (nulo = não informado). */
    public record Hours(@Min(0) @Max(20000) Integer mandatory, @Min(0) @Max(20000) Integer optional,
                        @Min(0) @Max(20000) Integer complementary) {
    }

    public record EnrollmentRequest(
            @NotNull(message = "Escolha o tipo do vínculo.") Kind kind,
            @NotBlank(message = "Informe o curso.") @Size(max = 160, message = "Curso com até 160 caracteres.") String course,
            @Size(max = 160, message = "Instituição com até 160 caracteres.") String institution,
            @Size(max = 40, message = "Matrícula com até 40 caracteres.") String registration,
            @Pattern(regexp = PERIOD, message = PERIOD_MESSAGE) String startPeriod,
            @NotNull(message = "Informe se o vínculo está ativo.") Boolean active,
            @NotNull(message = "Escolha uma cor.") @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida.") String color,
            @NotNull(message = "Informe a regra de aprovação.") @Valid Rules rules,
            @Valid Hours required,
            @Min(value = 0, message = "Horas complementares não podem ser negativas.") @Max(20000) Integer complementaryDone) {
    }

    public record Link(
            @NotBlank(message = "Dê um nome ao link.") @Size(max = 40, message = "Nome do link com até 40 caracteres.") String label,
            @NotBlank(message = "Informe o endereço.") @Size(max = 500, message = "Endereço longo demais.")
            @Pattern(regexp = "^https?://\\S+$", message = "O endereço precisa começar com http:// ou https://.") String url,
            @NotNull(message = "Escolha o tipo do link.") LinkKind kind) {
    }

    public record Unit(@NotNull @Size(max = 30, message = "Nome da unidade com até 30 caracteres.") String label,
                       @DecimalMin(value = "0", message = "Nota de 0 a 10.") @DecimalMax(value = "10", message = "Nota de 0 a 10.")
                       BigDecimal grade) {
    }

    public record CourseRequest(
            @Pattern(regexp = PERIOD, message = PERIOD_MESSAGE) String period,
            @Size(max = 20, message = "Código com até 20 caracteres.") String code,
            @NotBlank(message = "Informe o nome da matéria.") @Size(max = 200, message = "Nome com até 200 caracteres.") String name,
            @Min(0) @Max(2000) Integer hours,
            @Size(max = 20, message = "Turma com até 20 caracteres.") String classCode,
            @NotNull(message = "Escolha o tipo da matéria.") Category category,
            @NotNull(message = "Escolha a situação.") Status status,
            @Size(max = 160, message = "Professor com até 160 caracteres.") String teacher,
            @Email(message = "E-mail inválido.") @Size(max = 160) String teacherEmail,
            @Size(max = 80, message = "Sala com até 80 caracteres.") String room,
            @Size(max = 80, message = "Horário com até 80 caracteres.") String schedule,
            @Size(max = SchoolService.MAX_LINKS, message = "Até 12 links.") List<@NotNull @Valid Link> links,
            UUID commitmentId,
            UUID groupId) {
    }

    /** Notas da matéria (à mão ou coladas do SIGAA); {@code average}, {@code attendance} e {@code status} valem para a final. */
    public record GradesRequest(
            @NotNull(message = "Informe as unidades.") @Size(max = 8, message = "Até 8 unidades.") List<@NotNull @Valid Unit> units,
            @DecimalMin(value = "0", message = "Nota de 0 a 10.") @DecimalMax(value = "10", message = "Nota de 0 a 10.")
            BigDecimal recovery,
            @Min(0) @Max(999) Integer absences,
            @DecimalMin("0") @DecimalMax("10") BigDecimal average,
            @DecimalMin("0") @DecimalMax("100") BigDecimal attendance,
            @Size(max = 2) String concept,
            Status status) {
    }

    /** Matéria lida do histórico (SIGAA no navegador ou IA). */
    public record ImportCourse(
            @Pattern(regexp = PERIOD, message = PERIOD_MESSAGE) String period,
            @Size(max = 20) String code,
            @NotBlank @Size(max = 200) String name,
            @Min(0) @Max(2000) Integer hours,
            @Size(max = 20) String classCode,
            @NotNull Category category,
            @NotNull Status status,
            @DecimalMin("0") @DecimalMax("100") BigDecimal attendance,
            @DecimalMin("0") @DecimalMax("10") BigDecimal average,
            @Size(max = 2) String concept,
            @Size(max = 160) String teacher) {
    }

    public record ImportRequest(
            @NotNull(message = "Nada para importar.") @Size(max = 200, message = "Até 200 matérias por vez.")
            List<@NotNull @Valid ImportCourse> courses,
            @Valid Hours required,
            Boolean dryRun) {
    }

    public record ImportResult(int created, int updated) {
    }

    /** Lido pela IA: curso, instituição, carga exigida e matérias. */
    public record AiImport(String course, String institution, Hours required, List<ImportCourse> courses) {
    }

    public record AssessmentRequest(
            @NotBlank(message = "Dê um nome à avaliação.") @Size(max = 120, message = "Nome com até 120 caracteres.") String title,
            @NotNull(message = "Escolha o tipo.") SchoolAssessment.Kind kind,
            LocalDate dueDate,
            @JsonFormat(pattern = "HH:mm") LocalTime dueTime,
            @Min(value = 1, message = "Unidade de 1 a 8.") @Max(value = 8, message = "Unidade de 1 a 8.") Integer unit,
            @DecimalMin(value = "0.1", message = "Peso de 0,1 a 100.") @DecimalMax(value = "100", message = "Peso de 0,1 a 100.")
            BigDecimal weight,
            @DecimalMin(value = "0", message = "Nota de 0 a 10.") @DecimalMax(value = "10", message = "Nota de 0 a 10.") BigDecimal grade,
            Boolean createTask,
            @Min(0) @Max(10080) Integer reminderMinutes) {
    }

    public record AssessmentResponse(UUID id, String title, SchoolAssessment.Kind kind, LocalDate dueDate,
                                     @JsonFormat(pattern = "HH:mm") LocalTime dueTime, Integer unit, BigDecimal weight,
                                     BigDecimal grade, UUID taskId) {
    }

    /** {@code fromAssessments} = a nota veio das provas com nota daquela unidade. */
    public record UnitView(String label, BigDecimal grade, BigDecimal typed, boolean fromAssessments) {
    }

    /** Frequência pelo compromisso ligado; {@code remaining} = faltas que ainda cabem (negativo = passou). */
    public record AttendanceView(UUID commitmentId, String name, int presences, int absences, int remaining) {
    }

    /** Situação calculada pelas notas e os riscos (média abaixo do necessário, faltas perto do limite). */
    public record ResultView(BigDecimal partial, BigDecimal average, Status outcome, boolean gradeRisk, boolean absenceRisk) {
    }

    public record GroupRef(UUID id, String name) {
    }

    public record CourseResponse(UUID id, UUID enrollmentId, String period, String code, String name, Integer hours, String classCode,
                                 Category category, Status status, BigDecimal attendancePercent, BigDecimal average, String concept,
                                 List<UnitView> units, BigDecimal recovery, Integer absences, String teacher, String teacherEmail,
                                 String room, String schedule, List<Link> links, GroupRef group, UUID noteFolderId,
                                 AttendanceView attendance, ResultView result, AssessmentResponse next) {
    }

    public record CourseDetail(CourseResponse course, List<AssessmentResponse> assessments, String enrollmentName, Scale scale) {
    }

    public record EnrollmentResponse(UUID id, Kind kind, String course, String institution, String registration, String startPeriod,
                                     boolean active, String color, Rules rules, Hours required, int complementaryDone,
                                     BigDecimal index, String currentPeriod, int courses, int atRisk) {
    }

    public record PeriodView(String period, BigDecimal average, int hours, int courses) {
    }

    public record ProgressView(int mandatoryDone, int optionalDone, int complementaryDone, int ongoing, int totalDone,
                               Integer totalRequired, Integer termsLeft) {
    }

    /** Vínculo com as matérias (semestre mais novo primeiro), o índice por semestre e o progresso. */
    public record EnrollmentDetail(EnrollmentResponse enrollment, List<CourseResponse> courses, List<PeriodView> periods,
                                   ProgressView progress) {
    }

    public record SharedAssessment(String title, SchoolAssessment.Kind kind, LocalDate dueDate,
                                   @JsonFormat(pattern = "HH:mm") LocalTime dueTime) {
    }

    /** Matéria que alguém do grupo compartilhou: só links, professor, sala e as próximas avaliações (nunca notas). */
    public record SharedCourse(UUID id, String name, String code, String period, String ownerName, GroupRef group, String teacher,
                               String teacherEmail, String room, String schedule, List<Link> links, List<SharedAssessment> upcoming) {
    }

    public record Overview(List<EnrollmentResponse> enrollments, List<SharedCourse> shared, boolean aiEnabled) {
    }

    /** Registro no arquivo da conta. */
    public record EnrollmentExport(Kind kind, String course, String institution, String startPeriod, List<CourseExport> courses) {
    }

    public record CourseExport(String period, String code, String name, Integer hours, Status status, BigDecimal average, String concept,
                               List<Unit> units, BigDecimal recovery, List<Link> links, List<String> assessments) {
    }
}
