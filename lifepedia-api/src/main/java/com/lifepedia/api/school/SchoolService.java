package com.lifepedia.api.school;

import com.lifepedia.api.attendance.CommitmentLinks;
import com.lifepedia.api.attendance.CommitmentLinks.CommitmentCount;
import com.lifepedia.api.attendance.CommitmentLinks.LinkedCommitment;
import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRemovedEvent;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.group.UserGroup;
import com.lifepedia.api.group.UserGroupRepository;
import com.lifepedia.api.note.NoteLinks;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.school.SchoolCourse.Status;
import com.lifepedia.api.school.SchoolDtos.AssessmentResponse;
import com.lifepedia.api.school.SchoolDtos.AttendanceView;
import com.lifepedia.api.school.SchoolDtos.CourseDetail;
import com.lifepedia.api.school.SchoolDtos.CourseExport;
import com.lifepedia.api.school.SchoolDtos.CourseRequest;
import com.lifepedia.api.school.SchoolDtos.CourseResponse;
import com.lifepedia.api.school.SchoolDtos.EnrollmentDetail;
import com.lifepedia.api.school.SchoolDtos.EnrollmentExport;
import com.lifepedia.api.school.SchoolDtos.EnrollmentRequest;
import com.lifepedia.api.school.SchoolDtos.EnrollmentResponse;
import com.lifepedia.api.school.SchoolDtos.GradesRequest;
import com.lifepedia.api.school.SchoolDtos.GroupRef;
import com.lifepedia.api.school.SchoolDtos.Hours;
import com.lifepedia.api.school.SchoolDtos.ImportCourse;
import com.lifepedia.api.school.SchoolDtos.ImportRequest;
import com.lifepedia.api.school.SchoolDtos.ImportResult;
import com.lifepedia.api.school.SchoolDtos.Link;
import com.lifepedia.api.school.SchoolDtos.Overview;
import com.lifepedia.api.school.SchoolDtos.PeriodView;
import com.lifepedia.api.school.SchoolDtos.ProgressView;
import com.lifepedia.api.school.SchoolDtos.ResultView;
import com.lifepedia.api.school.SchoolDtos.Rules;
import com.lifepedia.api.school.SchoolDtos.SharedAssessment;
import com.lifepedia.api.school.SchoolDtos.SharedCourse;
import com.lifepedia.api.school.SchoolDtos.Unit;
import com.lifepedia.api.school.SchoolDtos.UnitView;
import com.lifepedia.api.school.SchoolEnrollment.Scale;
import com.lifepedia.api.school.SchoolMath.Graded;
import com.lifepedia.api.school.SchoolMath.Progress;
import com.lifepedia.api.school.SchoolMath.Result;
import com.lifepedia.api.school.SchoolMath.Rule;
import com.lifepedia.api.school.SchoolMath.Taken;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.ai.GeminiClient;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Vínculos, matérias, notas, importação do histórico, matérias compartilhadas com grupos e avisos de risco. */
@Service
public class SchoolService {

    static final int MAX_ENROLLMENTS = 10;
    static final int MAX_COURSES = 300;
    static final int MAX_LINKS = 12;
    static final int DEFAULT_UNITS = 3;
    /** Faltas que ainda cabem para contar como risco. */
    static final int ABSENCE_MARGIN = 2;
    static final String GRADE = "GRADE";
    static final String ABSENCES = "ABSENCES";
    private static final int SHARED_UPCOMING = 5;
    private static final TypeReference<List<Unit>> UNITS = new TypeReference<>() {
    };
    private static final TypeReference<List<Link>> LINKS = new TypeReference<>() {
    };
    private static final Comparator<SchoolCourse> COURSE_ORDER = Comparator
            .comparing(SchoolCourse::getPeriod, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(c -> c.getName().toLowerCase(Locale.ROOT));

    private final SchoolEnrollmentRepository enrollments;
    private final SchoolCourseRepository courses;
    private final SchoolAssessmentRepository assessments;
    private final CommitmentLinks commitments;
    private final NoteLinks notes;
    private final GroupAccess groupAccess;
    private final GroupMemberRepository members;
    private final UserGroupRepository groups;
    private final UserRepository users;
    private final NotificationService notifications;
    private final GeminiClient gemini;
    private final JsonMapper json;
    private final Clock clock;

    public SchoolService(SchoolEnrollmentRepository enrollments, SchoolCourseRepository courses, SchoolAssessmentRepository assessments,
                         CommitmentLinks commitments, NoteLinks notes, GroupAccess groupAccess, GroupMemberRepository members,
                         UserGroupRepository groups, UserRepository users, NotificationService notifications, GeminiClient gemini,
                         JsonMapper json, Clock clock) {
        this.enrollments = enrollments;
        this.courses = courses;
        this.assessments = assessments;
        this.commitments = commitments;
        this.notes = notes;
        this.groupAccess = groupAccess;
        this.members = members;
        this.groups = groups;
        this.users = users;
        this.notifications = notifications;
        this.gemini = gemini;
        this.json = json;
        this.clock = clock;
    }

    /** Vínculos com índice e riscos, matérias que os grupos compartilharam e se a IA está ligada. */
    @Transactional(readOnly = true)
    public Overview overview(UUID userId) {
        Context context = context(userId);
        List<EnrollmentResponse> list = enrollments.findByUserIdOrderByPositionAscCreatedAtAsc(userId).stream()
                .map(e -> enrollment(e, context)).toList();
        return new Overview(list, shared(userId), gemini.enabled());
    }

    @Transactional
    public EnrollmentResponse createEnrollment(UUID userId, EnrollmentRequest request) {
        if (enrollments.countByUserId(userId) >= MAX_ENROLLMENTS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_ENROLLMENTS + " vínculos.");
        }
        SchoolEnrollment enrollment = new SchoolEnrollment(userId, enrollments.maxPosition(userId) + 1);
        apply(enrollment, request);
        enrollments.save(enrollment);
        return enrollment(enrollment, context(userId));
    }

    @Transactional
    public EnrollmentResponse updateEnrollment(UUID userId, UUID id, EnrollmentRequest request) {
        SchoolEnrollment enrollment = findEnrollment(userId, id);
        apply(enrollment, request);
        return enrollment(enrollment, context(userId));
    }

    @Transactional
    public void deleteEnrollment(UUID userId, UUID id) {
        enrollments.delete(findEnrollment(userId, id));
    }

    /** Vínculo com as matérias, o índice de cada semestre e o progresso do curso. */
    @Transactional(readOnly = true)
    public EnrollmentDetail detail(UUID userId, UUID id) {
        SchoolEnrollment enrollment = findEnrollment(userId, id);
        Context context = context(userId);
        List<SchoolCourse> list = context.coursesOf(id);
        List<Taken> taken = list.stream().map(SchoolService::taken).toList();
        Progress p = SchoolMath.progress(taken, enrollment.getRequiredMandatory(), enrollment.getRequiredOptional(),
                enrollment.getRequiredComplementary(), enrollment.getComplementaryDone());
        return new EnrollmentDetail(enrollment(enrollment, context), list.stream().map(c -> course(c, enrollment, context)).toList(),
                SchoolMath.periods(taken).stream().map(x -> new PeriodView(x.period(), x.average(), x.hours(), x.courses())).toList(),
                new ProgressView(p.mandatoryDone(), p.optionalDone(), p.complementaryDone(), p.ongoing(), p.totalDone(),
                        p.totalRequired(), p.termsLeft()));
    }

    @Transactional
    public CourseDetail createCourse(UUID userId, UUID enrollmentId, CourseRequest request) {
        SchoolEnrollment enrollment = findEnrollment(userId, enrollmentId);
        if (courses.countByEnrollmentId(enrollmentId) >= MAX_COURSES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_COURSES + " matérias por vínculo.");
        }
        SchoolCourse course = new SchoolCourse(userId, enrollmentId);
        apply(userId, course, request);
        if (request.status().ongoing() && enrollment.getScale() == Scale.NUMERIC) course.setGrades(defaultUnits(), null, null);
        courses.save(course);
        return detail(userId, course);
    }

    @Transactional(readOnly = true)
    public CourseDetail course(UUID userId, UUID id) {
        return detail(userId, findCourse(userId, id));
    }

    @Transactional
    public CourseDetail updateCourse(UUID userId, UUID id, CourseRequest request) {
        SchoolCourse course = findCourse(userId, id);
        apply(userId, course, request);
        return detail(userId, course);
    }

    @Transactional
    public void deleteCourse(UUID userId, UUID id) {
        courses.delete(findCourse(userId, id));
    }

    /** Grava as notas (à mão ou coladas do SIGAA) e confere o risco na hora. */
    @Transactional
    public CourseDetail grades(UUID userId, UUID id, GradesRequest request) {
        SchoolCourse course = findCourse(userId, id);
        List<Unit> units = request.units().stream().map(u -> new Unit(u.label().trim(), u.grade())).toList();
        course.setGrades(json.writeValueAsString(units), request.recovery(), request.absences());
        if (request.status() != null) course.setStatus(request.status());
        if (request.average() != null || request.attendance() != null || request.concept() != null) {
            course.setFinal(request.attendance() != null ? request.attendance() : course.getAttendance(),
                    request.average() != null ? request.average() : course.getAverage(),
                    blank(request.concept()) == null ? course.getConcept() : request.concept().trim().toUpperCase(Locale.ROOT));
        }
        checkRisk(course, findEnrollment(userId, course.getEnrollmentId()), context(userId));
        return detail(userId, course);
    }

    /** Pasta da matéria no módulo Notas (a que já existe com o nome ou uma nova). */
    @Transactional
    public CourseDetail noteFolder(UUID userId, UUID id) {
        SchoolCourse course = findCourse(userId, id);
        if (course.getNoteFolderId() == null || notes.personalFolderId(userId, course.getNoteFolderId()).isEmpty()) {
            course.setNoteFolderId(notes.personalFolder(userId, course.getCode() == null ? course.getName()
                    : course.getCode() + " - " + course.getName()));
        }
        return detail(userId, course);
    }

    /**
     * Junta as matérias lidas do histórico: mesma matéria (semestre + código, ou nome) é atualizada, o resto é criado. Matéria
     * em andamento sem compromisso liga sozinha ao compromisso da Frequência com o código ou o nome dela.
     */
    @Transactional
    public ImportResult importCourses(UUID userId, UUID enrollmentId, ImportRequest request) {
        SchoolEnrollment enrollment = findEnrollment(userId, enrollmentId);
        List<SchoolCourse> existing = courses.findByEnrollmentId(enrollmentId);
        Map<String, SchoolCourse> byKey = new HashMap<>();
        existing.forEach(c -> byKey.put(key(c.getPeriod(), c.getCode(), c.getName()), c));
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        List<LinkedCommitment> options = dryRun ? List.of() : commitments.all(userId);
        int created = 0;
        int updated = 0;
        Set<String> seen = new LinkedHashSet<>();
        for (ImportCourse item : request.courses()) {
            String key = key(item.period(), blank(item.code()), item.name());
            if (!seen.add(key)) continue;
            SchoolCourse course = byKey.get(key);
            if (course == null) {
                if (existing.size() + created >= MAX_COURSES) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_COURSES + " matérias por vínculo.");
                }
                created++;
                if (dryRun) continue;
                course = new SchoolCourse(userId, enrollmentId);
                if (item.status().ongoing() && enrollment.getScale() == Scale.NUMERIC) course.setGrades(defaultUnits(), null, null);
            } else {
                updated++;
                if (dryRun) continue;
            }
            course.setBasics(item.period(), blank(item.code()), item.name().trim(), item.hours(),
                    blank(item.classCode()) == null ? course.getClassCode() : item.classCode().trim(), item.category(), item.status());
            course.setFinal(item.attendance(), item.average(), blank(item.concept()) == null ? null
                    : item.concept().trim().toUpperCase(Locale.ROOT));
            if (blank(item.teacher()) != null) course.setTeacher(item.teacher().trim());
            if (course.getCommitmentId() == null && item.status().ongoing()) {
                course.setCommitmentId(match(options, course));
            }
            courses.save(course);
        }
        if (!dryRun && request.required() != null) {
            Hours h = request.required();
            enrollment.setRequired(h.mandatory(), h.optional(), h.complementary());
        }
        return new ImportResult(created, updated);
    }

    /** Matérias que alguém compartilhou com grupos da pessoa (sem notas). */
    @Transactional(readOnly = true)
    public List<SharedCourse> shared(UUID userId) {
        List<UUID> groupIds = members.findByUserId(userId).stream().map(GroupMember::getGroupId).toList();
        if (groupIds.isEmpty()) return List.of();
        List<SchoolCourse> list = courses.findByGroupIdInAndUserIdNot(groupIds, userId);
        if (list.isEmpty()) return List.of();
        Map<UUID, String> owners = users.findAllById(list.stream().map(SchoolCourse::getUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
        Map<UUID, String> names = groupNames(list);
        Map<UUID, List<SchoolAssessment>> byCourse = assessments.findByCourseIdIn(list.stream().map(SchoolCourse::getId).toList())
                .stream().collect(Collectors.groupingBy(SchoolAssessment::getCourseId));
        LocalDate today = LocalDate.now(clock);
        return list.stream().sorted(COURSE_ORDER).map(c -> new SharedCourse(c.getId(), c.getName(), c.getCode(), c.getPeriod(),
                owners.getOrDefault(c.getUserId(), ""), new GroupRef(c.getGroupId(), names.getOrDefault(c.getGroupId(), "")),
                c.getTeacher(), c.getTeacherEmail(), c.getRoom(), c.getSchedule(), links(c),
                byCourse.getOrDefault(c.getId(), List.of()).stream().filter(a -> a.getDueDate() != null && !a.getDueDate().isBefore(today))
                        .sorted(Comparator.comparing(SchoolAssessment::getDueDate)).limit(SHARED_UPCOMING)
                        .map(a -> new SharedAssessment(a.getTitle(), a.getKind(), a.getDueDate(), a.getDueTime())).toList()))
                .toList();
    }

    /** Matérias em andamento de todo mundo: avisa média abaixo do necessário e faltas perto do limite (uma vez por motivo). */
    @Transactional
    public int checkRisks() {
        Map<UUID, List<SchoolCourse>> byUser = courses.findByStatusIn(List.of(Status.MATR, Status.REC)).stream()
                .collect(Collectors.groupingBy(SchoolCourse::getUserId));
        int sent = 0;
        for (Map.Entry<UUID, List<SchoolCourse>> entry : byUser.entrySet()) {
            Context context = context(entry.getKey());
            for (SchoolCourse course : entry.getValue()) {
                SchoolEnrollment enrollment = context.enrollmentById.get(course.getEnrollmentId());
                if (enrollment != null && enrollment.isActive()) sent += checkRisk(course, enrollment, context);
            }
        }
        return sent;
    }

    @Transactional(readOnly = true)
    public List<EnrollmentExport> exportData(UUID userId) {
        Context context = context(userId);
        return enrollments.findByUserIdOrderByPositionAscCreatedAtAsc(userId).stream()
                .map(e -> new EnrollmentExport(e.getKind(), e.getCourse(), e.getInstitution(), e.getStartPeriod(),
                        context.coursesOf(e.getId()).stream().map(c -> new CourseExport(c.getPeriod(), c.getCode(), c.getName(),
                                c.getHours(), c.getStatus(), c.getAverage(), c.getConcept(), units(c), c.getRecovery(), links(c),
                                context.assessmentsOf(c.getId()).stream().map(SchoolAssessment::getTitle).toList())).toList()))
                .toList();
    }

    /** Quem sai do grupo descompartilha as matérias dele ali. */
    @EventListener
    public void onMemberRemoved(GroupMemberRemovedEvent event) {
        courses.findByUserIdAndGroupId(event.userId(), event.groupId()).forEach(c -> c.setGroupId(null));
    }

    SchoolCourse findCourse(UUID userId, UUID id) {
        return courses.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Matéria não encontrada."));
    }

    /** Detalhe da matéria com as avaliações. */
    CourseDetail detail(UUID userId, SchoolCourse course) {
        SchoolEnrollment enrollment = findEnrollment(userId, course.getEnrollmentId());
        Context context = context(userId);
        List<AssessmentResponse> list = context.assessmentsOf(course.getId()).stream()
                .sorted(Comparator.comparing(SchoolAssessment::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SchoolAssessment::getTitle))
                .map(SchoolService::assessment).toList();
        return new CourseDetail(course(course, enrollment, context), list, enrollment.getCourse(), enrollment.getScale());
    }

    private int checkRisk(SchoolCourse course, SchoolEnrollment enrollment, Context context) {
        if (!course.getStatus().ongoing()) return 0;
        ResultView result = result(course, enrollment, context);
        Set<String> warned = new LinkedHashSet<>(course.getRiskWarned() == null ? List.of()
                : Arrays.asList(course.getRiskWarned().split(",")));
        int sent = 0;
        sent += warn(course, warned, GRADE, result.gradeRisk(), result.partial() == null ? "" : result.partial().toPlainString());
        AttendanceView attendance = attendance(course, context);
        sent += warn(course, warned, ABSENCES, result.absenceRisk(), attendance == null ? "" : String.valueOf(attendance.remaining()));
        String value = warned.isEmpty() ? null : String.join(",", warned);
        if (!Objects.equals(value, course.getRiskWarned())) course.setRiskWarned(value);
        return sent;
    }

    private int warn(SchoolCourse course, Set<String> warned, String reason, boolean risk, String value) {
        if (!risk) {
            warned.remove(reason);
            return 0;
        }
        if (!warned.add(reason)) return 0;
        Map<String, String> data = new LinkedHashMap<>();
        data.put("courseId", course.getId().toString());
        data.put("name", course.getName());
        data.put("reason", reason);
        data.put("value", value);
        notifications.notify(course.getUserId(), NotificationType.SCHOOL_RISK, null, null, data);
        return 1;
    }

    private void apply(SchoolEnrollment enrollment, EnrollmentRequest request) {
        Rules rules = request.rules();
        if (rules.lowAverage().compareTo(rules.passAverage()) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "A média mínima não pode passar da média para aprovar.");
        }
        enrollment.set(request.kind(), request.course().trim(), blank(request.institution()), blank(request.registration()),
                blank(request.startPeriod()), request.active(), request.color().toLowerCase(Locale.ROOT));
        enrollment.setRules(rules.scale(), rules.passAverage(), rules.lowAverage(), rules.minUnit(), rules.minAttendance());
        Hours hours = request.required() == null ? new Hours(null, null, null) : request.required();
        enrollment.setRequired(hours.mandatory(), hours.optional(), hours.complementary());
        enrollment.setComplementaryDone(request.complementaryDone() == null ? 0 : request.complementaryDone());
    }

    private void apply(UUID userId, SchoolCourse course, CourseRequest request) {
        if (request.commitmentId() != null && commitments.personal(userId, request.commitmentId()).isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Compromisso da Frequência inválido.");
        }
        if (request.groupId() != null) groupAccess.requireMember(request.groupId(), userId);
        List<Link> links = request.links() == null ? List.of() : request.links().stream()
                .map(l -> new Link(l.label().trim(), l.url().trim(), l.kind())).toList();
        course.setBasics(blank(request.period()), blank(request.code()), request.name().trim(), request.hours(), blank(request.classCode()),
                request.category(), request.status());
        course.setInfo(blank(request.teacher()), blank(request.teacherEmail()), blank(request.room()), blank(request.schedule()),
                json.writeValueAsString(links));
        course.setCommitmentId(request.commitmentId());
        course.setGroupId(request.groupId());
    }

    private EnrollmentResponse enrollment(SchoolEnrollment e, Context context) {
        List<SchoolCourse> list = context.coursesOf(e.getId());
        String current = list.stream().filter(c -> c.getStatus().ongoing() && c.getPeriod() != null).map(SchoolCourse::getPeriod)
                .max(Comparator.naturalOrder()).orElse(null);
        int atRisk = (int) list.stream().filter(c -> c.getStatus().ongoing()).map(c -> result(c, e, context))
                .filter(r -> r.gradeRisk() || r.absenceRisk()).count();
        return new EnrollmentResponse(e.getId(), e.getKind(), e.getCourse(), e.getInstitution(), e.getRegistration(), e.getStartPeriod(),
                e.isActive(), e.getColor(), rules(e), new Hours(e.getRequiredMandatory(), e.getRequiredOptional(),
                e.getRequiredComplementary()), e.getComplementaryDone(),
                SchoolMath.index(list.stream().map(SchoolService::taken).toList()), current, list.size(), atRisk);
    }

    private CourseResponse course(SchoolCourse c, SchoolEnrollment e, Context context) {
        List<Unit> typed = units(c);
        List<SchoolAssessment> list = context.assessmentsOf(c.getId());
        List<BigDecimal> effective = effective(typed, list);
        List<UnitView> views = new ArrayList<>();
        for (int i = 0; i < typed.size(); i++) {
            int unit = i + 1;
            boolean fromAssessments = list.stream().anyMatch(a -> a.getGrade() != null && a.getUnit() != null && a.getUnit() == unit);
            views.add(new UnitView(typed.get(i).label(), effective.get(i), typed.get(i).grade(), fromAssessments));
        }
        ResultView result = result(c, e, context);
        LocalDate today = LocalDate.now(clock);
        AssessmentResponse next = list.stream().filter(a -> a.getDueDate() != null && !a.getDueDate().isBefore(today))
                .min(Comparator.comparing(SchoolAssessment::getDueDate)).map(SchoolService::assessment).orElse(null);
        GroupRef group = c.getGroupId() == null ? null : new GroupRef(c.getGroupId(), context.groupName(c.getGroupId()));
        return new CourseResponse(c.getId(), c.getEnrollmentId(), c.getPeriod(), c.getCode(), c.getName(), c.getHours(), c.getClassCode(),
                c.getCategory(), c.getStatus(), c.getAttendance(), c.getStatus().ongoing() ? result.average() : c.getAverage(),
                c.getConcept(), views, c.getRecovery(), c.getAbsences(), c.getTeacher(), c.getTeacherEmail(), c.getRoom(),
                c.getSchedule(), links(c), group, c.getNoteFolderId(), attendance(c, context), result, next);
    }

    private ResultView result(SchoolCourse c, SchoolEnrollment e, Context context) {
        if (!c.getStatus().ongoing()) return new ResultView(null, c.getAverage(), c.getStatus(), false, false);
        Result r = SchoolMath.result(new Rule(e.getScale(), e.getPassAverage(), e.getLowAverage(), e.getMinUnit(), e.getMinAttendance()),
                effective(units(c), context.assessmentsOf(c.getId())), c.getRecovery(), c.getConcept());
        AttendanceView attendance = attendance(c, context);
        boolean absenceRisk = attendance != null && attendance.absences() > 0 && attendance.remaining() <= ABSENCE_MARGIN;
        return new ResultView(r.partial(), r.average(), r.outcome(), r.risk(), absenceRisk);
    }

    private AttendanceView attendance(SchoolCourse c, Context context) {
        if (c.getCommitmentId() == null) return null;
        CommitmentCount count = context.counts().get(c.getCommitmentId());
        return count == null ? null : new AttendanceView(count.id(), count.name(), count.presences(), count.absences(), count.remaining());
    }

    private Context context(UUID userId) {
        List<SchoolEnrollment> mine = enrollments.findByUserIdOrderByPositionAscCreatedAtAsc(userId);
        List<SchoolCourse> list = courses.findByUserId(userId);
        Map<UUID, List<SchoolAssessment>> byCourse = list.isEmpty() ? Map.of()
                : assessments.findByCourseIdIn(list.stream().map(SchoolCourse::getId).toList()).stream()
                .collect(Collectors.groupingBy(SchoolAssessment::getCourseId));
        return new Context(userId, mine, list, byCourse, groupNames(list));
    }

    private Map<UUID, String> groupNames(List<SchoolCourse> list) {
        Set<UUID> ids = list.stream().map(SchoolCourse::getGroupId).filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : groups.findAllById(ids).stream().collect(Collectors.toMap(UserGroup::getId, UserGroup::getName));
    }

    private List<Unit> units(SchoolCourse course) {
        try {
            return json.readValue(course.getUnits(), UNITS);
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private List<Link> links(SchoolCourse course) {
        try {
            return json.readValue(course.getLinks(), LINKS);
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private String defaultUnits() {
        List<Unit> units = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_UNITS; i++) units.add(new Unit("Unid. " + i, null));
        return json.writeValueAsString(units);
    }

    private SchoolEnrollment findEnrollment(UUID userId, UUID id) {
        return enrollments.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Vínculo não encontrado."));
    }

    private static List<BigDecimal> effective(List<Unit> typed, List<SchoolAssessment> list) {
        return SchoolMath.units(typed.stream().map(Unit::grade).toList(),
                list.stream().map(a -> new Graded(a.getUnit(), a.getWeight(), a.getGrade())).toList());
    }

    private static Rules rules(SchoolEnrollment e) {
        return new Rules(e.getScale(), e.getPassAverage(), e.getLowAverage(), e.getMinUnit(), e.getMinAttendance());
    }

    private static Taken taken(SchoolCourse c) {
        return new Taken(c.getPeriod(), c.getCategory(), c.getStatus(), c.getHours(), c.getAverage());
    }

    static AssessmentResponse assessment(SchoolAssessment a) {
        return new AssessmentResponse(a.getId(), a.getTitle(), a.getKind(), a.getDueDate(), a.getDueTime(), a.getUnit(), a.getWeight(),
                a.getGrade(), a.getTaskId());
    }

    /** Compromisso com o código da matéria no nome ou com o mesmo nome (sem acento e maiúscula). */
    private static UUID match(List<LinkedCommitment> options, SchoolCourse course) {
        String name = normalize(course.getName());
        String code = course.getCode() == null ? null : normalize(course.getCode());
        return options.stream().filter(o -> {
            String option = normalize(o.name());
            return code != null && option.contains(code) || option.equals(name);
        }).map(LinkedCommitment::id).findFirst().orElse(null);
    }

    private static String key(String period, String code, String name) {
        return (period == null ? "-" : period) + "|" + (code != null ? code.toUpperCase(Locale.ROOT) : "n:" + normalize(name));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Dados da pessoa carregados uma vez por requisição. */
    private final class Context {
        private final UUID userId;
        private final Map<UUID, SchoolEnrollment> enrollmentById;
        private final Map<UUID, List<SchoolCourse>> byEnrollment;
        private final Map<UUID, List<SchoolAssessment>> byCourse;
        private final Map<UUID, String> groupNames;
        private Map<UUID, CommitmentCount> counts;

        Context(UUID userId, List<SchoolEnrollment> enrollments, List<SchoolCourse> list, Map<UUID, List<SchoolAssessment>> byCourse,
                Map<UUID, String> groupNames) {
            this.userId = userId;
            this.enrollmentById = enrollments.stream().collect(Collectors.toMap(SchoolEnrollment::getId, Function.identity()));
            this.byEnrollment = list.stream().sorted(COURSE_ORDER).collect(Collectors.groupingBy(SchoolCourse::getEnrollmentId,
                    LinkedHashMap::new, Collectors.toList()));
            this.byCourse = byCourse;
            this.groupNames = groupNames;
        }

        List<SchoolCourse> coursesOf(UUID enrollmentId) {
            return byEnrollment.getOrDefault(enrollmentId, List.of());
        }

        List<SchoolAssessment> assessmentsOf(UUID courseId) {
            return byCourse.getOrDefault(courseId, List.of());
        }

        String groupName(UUID groupId) {
            return groupNames.getOrDefault(groupId, "");
        }

        /** Contagem da Frequência, só quando alguma matéria tem compromisso. */
        Map<UUID, CommitmentCount> counts() {
            if (counts == null) {
                boolean linked = byEnrollment.values().stream().flatMap(List::stream).anyMatch(c -> c.getCommitmentId() != null);
                counts = linked ? commitments.counts(userId) : Map.of();
            }
            return counts;
        }
    }
}
