package com.lifepedia.api.integration;

import com.lifepedia.api.integration.IntegrationDtos.AccountResponse;
import com.lifepedia.api.integration.IntegrationDtos.DuolingoRequest;
import com.lifepedia.api.integration.IntegrationDtos.EventResponse;
import com.lifepedia.api.integration.IntegrationDtos.GoogleStart;
import com.lifepedia.api.integration.IntegrationDtos.LabelRequest;
import com.lifepedia.api.integration.IntegrationDtos.Overview;
import com.lifepedia.api.integration.IntegrationDtos.ReconnectRequest;
import com.lifepedia.api.integration.IntegrationDtos.Summary;
import com.lifepedia.api.integration.IntegrationDtos.SyncEntry;
import com.lifepedia.api.integration.IntegrationDtos.TokenRequest;
import com.lifepedia.api.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {

    private final IntegrationService service;

    public IntegrationController(IntegrationService service) {
        this.service = service;
    }

    @GetMapping
    public Overview overview(@AuthenticationPrincipal Jwt jwt) {
        return service.overview(CurrentUser.id(jwt));
    }

    @GetMapping("/summary")
    public Summary summary(@AuthenticationPrincipal Jwt jwt) {
        return service.summary(CurrentUser.id(jwt));
    }

    @PostMapping("/gitlab")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse gitlab(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TokenRequest request) {
        return service.connectToken(CurrentUser.id(jwt), IntegrationProvider.GITLAB, request);
    }

    @PostMapping("/github")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse github(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TokenRequest request) {
        return service.connectToken(CurrentUser.id(jwt), IntegrationProvider.GITHUB, request);
    }

    @PostMapping("/duolingo")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse duolingo(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DuolingoRequest request) {
        return service.connectDuolingo(CurrentUser.id(jwt), request);
    }

    @PostMapping("/google/start")
    public GoogleStart googleStart(@AuthenticationPrincipal Jwt jwt) {
        return service.googleStart(CurrentUser.id(jwt));
    }

    /** Volta do consentimento do Google (navegação do próprio navegador, com o cookie de sessão). */
    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String code,
                                               @RequestParam(required = false) String state,
                                               @RequestParam(required = false) String error) {
        String target = service.googleCallback(CurrentUser.id(jwt), code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target).build();
    }

    @PutMapping("/{id}")
    public AccountResponse label(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody LabelRequest request) {
        return service.label(CurrentUser.id(jwt), id, request);
    }

    @PutMapping("/{id}/token")
    public AccountResponse reconnect(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody ReconnectRequest request) {
        return service.reconnect(CurrentUser.id(jwt), id, request);
    }

    @PostMapping("/{id}/sync")
    public AccountResponse sync(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.syncNow(CurrentUser.id(jwt), id);
    }

    @GetMapping("/{id}/syncs")
    public List<SyncEntry> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.history(CurrentUser.id(jwt), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.disconnect(CurrentUser.id(jwt), id);
    }

    @GetMapping("/calendar")
    public List<EventResponse> calendar(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.calendar(CurrentUser.id(jwt), from, to);
    }
}
