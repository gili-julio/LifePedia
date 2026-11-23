package com.lifepedia.api.school;

import com.lifepedia.api.school.SchoolDtos.AiImport;
import com.lifepedia.api.school.SchoolDtos.AssessmentRequest;
import com.lifepedia.api.school.SchoolDtos.CourseDetail;
import com.lifepedia.api.school.SchoolDtos.CourseRequest;
import com.lifepedia.api.school.SchoolDtos.EnrollmentDetail;
import com.lifepedia.api.school.SchoolDtos.EnrollmentRequest;
import com.lifepedia.api.school.SchoolDtos.EnrollmentResponse;
import com.lifepedia.api.school.SchoolDtos.GradesRequest;
import com.lifepedia.api.school.SchoolDtos.ImportRequest;
import com.lifepedia.api.school.SchoolDtos.ImportResult;
import com.lifepedia.api.school.SchoolDtos.Overview;
import com.lifepedia.api.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/school")
public class SchoolController {

    private final SchoolService school;
    private final SchoolAssessmentService assessments;
    private final SchoolAiService ai;

    public SchoolController(SchoolService school, SchoolAssessmentService assessments, SchoolAiService ai) {
        this.school = school;
        this.assessments = assessments;
        this.ai = ai;
    }

    @GetMapping
    public Overview overview(@AuthenticationPrincipal Jwt jwt) {
        return school.overview(CurrentUser.id(jwt));
    }

    @PostMapping("/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResponse createEnrollment(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EnrollmentRequest request) {
        return school.createEnrollment(CurrentUser.id(jwt), request);
    }

    @GetMapping("/enrollments/{id}")
    public EnrollmentDetail enrollment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return school.detail(CurrentUser.id(jwt), id);
    }

    @PutMapping("/enrollments/{id}")
    public EnrollmentResponse updateEnrollment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                               @Valid @RequestBody EnrollmentRequest request) {
        return school.updateEnrollment(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/enrollments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEnrollment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        school.deleteEnrollment(CurrentUser.id(jwt), id);
    }

    @PostMapping("/enrollments/{id}/import")
    public ImportResult importCourses(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ImportRequest request) {
        return school.importCourses(CurrentUser.id(jwt), id, request);
    }

    @PostMapping("/enrollments/{id}/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseDetail createCourse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody CourseRequest request) {
        return school.createCourse(CurrentUser.id(jwt), id, request);
    }

    @GetMapping("/courses/{id}")
    public CourseDetail course(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return school.course(CurrentUser.id(jwt), id);
    }

    @PutMapping("/courses/{id}")
    public CourseDetail updateCourse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody CourseRequest request) {
        return school.updateCourse(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/courses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCourse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        school.deleteCourse(CurrentUser.id(jwt), id);
    }

    @PutMapping("/courses/{id}/grades")
    public CourseDetail grades(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody GradesRequest request) {
        return school.grades(CurrentUser.id(jwt), id, request);
    }

    @PostMapping("/courses/{id}/notes-folder")
    public CourseDetail noteFolder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return school.noteFolder(CurrentUser.id(jwt), id);
    }

    @PostMapping("/courses/{id}/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseDetail createAssessment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody AssessmentRequest request) {
        return assessments.create(CurrentUser.id(jwt), id, request);
    }

    @PutMapping("/courses/{id}/assessments/{assessmentId}")
    public CourseDetail updateAssessment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID assessmentId,
                                         @Valid @RequestBody AssessmentRequest request) {
        return assessments.update(CurrentUser.id(jwt), id, assessmentId, request);
    }

    @DeleteMapping("/courses/{id}/assessments/{assessmentId}")
    public CourseDetail deleteAssessment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID assessmentId) {
        return assessments.delete(CurrentUser.id(jwt), id, assessmentId);
    }

    @PostMapping(path = "/import/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AiImport readWithAi(@AuthenticationPrincipal Jwt jwt,
                               @RequestParam(name = "files", required = false) List<MultipartFile> files) {
        return ai.read(CurrentUser.id(jwt), files);
    }
}
