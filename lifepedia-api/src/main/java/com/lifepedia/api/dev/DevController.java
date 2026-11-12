package com.lifepedia.api.dev;

import com.lifepedia.api.dev.DevDtos.ActivityResponse;
import com.lifepedia.api.dev.DevDtos.Daily;
import com.lifepedia.api.dev.DevDtos.OrderRequest;
import com.lifepedia.api.dev.DevDtos.ProjectDetail;
import com.lifepedia.api.dev.DevDtos.ProjectRequest;
import com.lifepedia.api.dev.DevDtos.ProjectSummary;
import com.lifepedia.api.dev.DevDtos.RepoOption;
import com.lifepedia.api.dev.DevDtos.RepoRequest;
import com.lifepedia.api.dev.DevDtos.RepoResponse;
import com.lifepedia.api.dev.DevDtos.RulesRequest;
import com.lifepedia.api.dev.DevDtos.Today;
import com.lifepedia.api.shared.CurrentUser;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dev")
public class DevController {

    private final DevService service;
    private final DevDailyService daily;

    public DevController(DevService service, DevDailyService daily) {
        this.service = service;
        this.daily = daily;
    }

    @GetMapping("/projects")
    public List<ProjectSummary> projects(@AuthenticationPrincipal Jwt jwt) {
        return service.projects(CurrentUser.id(jwt));
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectSummary create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProjectRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @PutMapping("/projects/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrderRequest request) {
        service.reorder(CurrentUser.id(jwt), request.ids());
    }

    @GetMapping("/projects/{id}")
    public ProjectDetail detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.detail(CurrentUser.id(jwt), id);
    }

    @PutMapping("/projects/{id}")
    public ProjectSummary update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ProjectRequest request) {
        return service.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(CurrentUser.id(jwt), id);
    }

    @PostMapping("/projects/{id}/repos")
    @ResponseStatus(HttpStatus.CREATED)
    public RepoResponse addRepo(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody RepoRequest request) {
        return service.addRepo(CurrentUser.id(jwt), id, request);
    }

    @GetMapping("/repo-options")
    public List<RepoOption> repoOptions(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID accountId,
                                        @RequestParam(required = false) String q) {
        return service.repoOptions(CurrentUser.id(jwt), accountId, q == null || q.length() <= 100 ? q : q.substring(0, 100));
    }

    @PutMapping("/repos/{id}")
    public RepoResponse rules(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody RulesRequest request) {
        return service.updateRules(CurrentUser.id(jwt), id, request);
    }

    @GetMapping("/repos/{id}/labels")
    public List<String> labels(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.labels(CurrentUser.id(jwt), id);
    }

    @DeleteMapping("/repos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRepo(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.deleteRepo(CurrentUser.id(jwt), id);
    }

    @GetMapping("/today")
    public Today today(@AuthenticationPrincipal Jwt jwt) {
        return service.today(CurrentUser.id(jwt));
    }

    @GetMapping("/activity")
    public ActivityResponse activity(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "12") int weeks) {
        return service.activity(CurrentUser.id(jwt), weeks);
    }

    @PostMapping("/daily")
    public Daily daily(@AuthenticationPrincipal Jwt jwt) {
        return daily.generate(CurrentUser.id(jwt));
    }
}
