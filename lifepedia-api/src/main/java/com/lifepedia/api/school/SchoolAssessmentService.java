package com.lifepedia.api.school;

import com.lifepedia.api.school.SchoolDtos.AssessmentRequest;
import com.lifepedia.api.school.SchoolDtos.CourseDetail;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.task.TaskLinks;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Provas e trabalhos da matéria; com data, podem virar tarefa pessoal com lembrete (etiqueta = código ou nome da matéria). */
@Service
public class SchoolAssessmentService {

    static final int MAX_ASSESSMENTS = 40;
    private static final String TASK_COLOR = "#2563eb";

    private final SchoolService school;
    private final SchoolAssessmentRepository assessments;
    private final TaskLinks tasks;

    public SchoolAssessmentService(SchoolService school, SchoolAssessmentRepository assessments, TaskLinks tasks) {
        this.school = school;
        this.assessments = assessments;
        this.tasks = tasks;
    }

    @Transactional
    public CourseDetail create(UUID userId, UUID courseId, AssessmentRequest request) {
        SchoolCourse course = school.findCourse(userId, courseId);
        if (assessments.countByCourseId(courseId) >= MAX_ASSESSMENTS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_ASSESSMENTS + " avaliações por matéria.");
        }
        SchoolAssessment assessment = new SchoolAssessment(courseId, userId);
        apply(assessment, request);
        assessments.save(assessment);
        if (Boolean.TRUE.equals(request.createTask()) && assessment.getDueDate() != null) {
            tasks.createPersonal(userId, taskTitle(course, assessment), null, assessment.getDueDate(), assessment.getDueTime(),
                    request.reminderMinutes(), label(course), TASK_COLOR).ifPresent(assessment::setTaskId);
        }
        return school.detail(userId, course);
    }

    @Transactional
    public CourseDetail update(UUID userId, UUID courseId, UUID id, AssessmentRequest request) {
        SchoolCourse course = school.findCourse(userId, courseId);
        SchoolAssessment assessment = find(courseId, id);
        apply(assessment, request);
        if (assessment.getTaskId() != null) {
            tasks.reschedulePersonal(userId, assessment.getTaskId(), taskTitle(course, assessment), assessment.getDueDate(),
                    assessment.getDueTime(), request.reminderMinutes());
        } else if (Boolean.TRUE.equals(request.createTask()) && assessment.getDueDate() != null) {
            tasks.createPersonal(userId, taskTitle(course, assessment), null, assessment.getDueDate(), assessment.getDueTime(),
                    request.reminderMinutes(), label(course), TASK_COLOR).ifPresent(assessment::setTaskId);
        }
        return school.detail(userId, course);
    }

    /** Exclui a avaliação; a tarefa criada por ela continua nas Tarefas. */
    @Transactional
    public CourseDetail delete(UUID userId, UUID courseId, UUID id) {
        SchoolCourse course = school.findCourse(userId, courseId);
        assessments.delete(find(courseId, id));
        assessments.flush();
        return school.detail(userId, course);
    }

    private SchoolAssessment find(UUID courseId, UUID id) {
        return assessments.findByIdAndCourseId(id, courseId).orElseThrow(() -> new NotFoundException("Avaliação não encontrada."));
    }

    private static void apply(SchoolAssessment assessment, AssessmentRequest request) {
        assessment.set(request.title().trim(), request.kind(), request.dueDate(), request.dueTime(), request.unit(),
                request.weight() == null ? BigDecimal.ONE : request.weight(), request.grade());
    }

    private static String taskTitle(SchoolCourse course, SchoolAssessment assessment) {
        return assessment.getTitle() + " - " + course.getName();
    }

    private static String label(SchoolCourse course) {
        return course.getCode() != null ? course.getCode() : course.getName();
    }
}
