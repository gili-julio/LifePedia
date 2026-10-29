package com.lifepedia.api.task;

import com.lifepedia.api.shared.CurrentUser;
import com.lifepedia.api.task.TaskDtos.AgendaSettings;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preferência pessoal da agenda (vale também na agenda dos grupos). */
@RestController
@RequestMapping("/api/tasks/agenda-settings")
public class AgendaPreferenceController {

    private final AgendaPreferenceService service;

    public AgendaPreferenceController(AgendaPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public AgendaSettings get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(CurrentUser.id(jwt));
    }

    @PutMapping
    public AgendaSettings save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AgendaSettings request) {
        return service.save(CurrentUser.id(jwt), request);
    }
}
