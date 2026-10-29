package com.lifepedia.api.attendance;

import com.lifepedia.api.attendance.AttendanceDtos.AgendaItem;
import com.lifepedia.api.attendance.AttendanceDtos.CommitmentRequest;
import com.lifepedia.api.attendance.AttendanceDtos.CommitmentResponse;
import com.lifepedia.api.attendance.AttendanceDtos.Day;
import com.lifepedia.api.attendance.AttendanceDtos.ImportRequest;
import com.lifepedia.api.attendance.AttendanceDtos.MarkRequest;
import com.lifepedia.api.shared.CurrentUser;
import com.lifepedia.api.task.TaskLinks.LinkedTask;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Frequência: compromissos com limite de faltas (lista completa, como grupos: no máximo 50 por pessoa). */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @GetMapping
    public List<CommitmentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommitmentResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CommitmentRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    /** Cria (ou usa o {@code targetId}) e grava as marcações de uma vez; o texto do SIGAA é lido no navegador. */
    @PostMapping("/import")
    public CommitmentResponse importDays(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ImportRequest request) {
        return service.importDays(CurrentUser.id(jwt), request);
    }

    @PutMapping("/{id}")
    public CommitmentResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody CommitmentRequest request) {
        return service.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(CurrentUser.id(jwt), id);
    }

    @GetMapping("/{id}/days")
    public List<Day> days(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.days(CurrentUser.id(jwt), id);
    }

    @PutMapping("/{id}/days/{date}")
    public Day mark(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable LocalDate date,
                    @RequestBody MarkRequest request) {
        return service.mark(CurrentUser.id(jwt), id, date, request);
    }

    /** Dias dos compromissos de {@code from} a {@code to} (até 62 dias), para a agenda. */
    @GetMapping("/agenda")
    public List<AgendaItem> agenda(@AuthenticationPrincipal Jwt jwt, @RequestParam LocalDate from,
                                   @RequestParam LocalDate to) {
        return service.agenda(CurrentUser.id(jwt), from, to);
    }

    @GetMapping("/tasks")
    public List<LinkedTask> linkable(@AuthenticationPrincipal Jwt jwt) {
        return service.linkable(CurrentUser.id(jwt));
    }
}
