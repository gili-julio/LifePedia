package com.lifepedia.api.admin;

import com.lifepedia.api.admin.AdminDtos.ServiceLimit;
import com.lifepedia.api.admin.AdminDtos.ActiveAnnouncement;
import com.lifepedia.api.admin.AdminDtos.AdminRequest;
import com.lifepedia.api.admin.AdminDtos.AdminUser;
import com.lifepedia.api.admin.AdminDtos.AdminUserDetail;
import com.lifepedia.api.admin.AdminDtos.AiOverview;
import com.lifepedia.api.admin.AdminDtos.AnnouncementRequest;
import com.lifepedia.api.admin.AdminDtos.AnnouncementResponse;
import com.lifepedia.api.admin.AdminDtos.AuditResponse;
import com.lifepedia.api.admin.AdminDtos.BlockRequest;
import com.lifepedia.api.admin.AdminDtos.BroadcastRequest;
import com.lifepedia.api.admin.AdminDtos.BroadcastResponse;
import com.lifepedia.api.admin.AdminDtos.Modules;
import com.lifepedia.api.admin.AdminDtos.Overview;
import com.lifepedia.api.admin.AdminDtos.SystemStatus;
import com.lifepedia.api.admin.AdminDtos.UserFilter;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.CurrentUser;
import com.lifepedia.api.shared.PageResponse;
import com.lifepedia.api.user.UserRepository;
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

/** Painel de administração. A cadeia de segurança já barra quem não é admin ({@link AdminAccess}); aqui confere de novo. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService service;
    private final AdminUserService userService;
    private final AdminStatusService status;
    private final AnnouncementService announcements;
    private final AuditService audit;
    private final UserRepository users;

    public AdminController(AdminService service, AdminUserService userService, AdminStatusService status,
                           AnnouncementService announcements, AuditService audit, UserRepository users) {
        this.service = service;
        this.userService = userService;
        this.status = status;
        this.announcements = announcements;
        this.audit = audit;
        this.users = users;
    }

    @GetMapping("/overview")
    public Overview overview(@AuthenticationPrincipal Jwt jwt) {
        admin(jwt);
        return service.overview();
    }

    @GetMapping("/modules")
    public Modules modules(@AuthenticationPrincipal Jwt jwt) {
        admin(jwt);
        return service.modules();
    }

    @GetMapping("/ai")
    public AiOverview ai(@AuthenticationPrincipal Jwt jwt) {
        admin(jwt);
        return service.ai();
    }

    @GetMapping("/limits")
    public List<ServiceLimit> limits(@AuthenticationPrincipal Jwt jwt) {
        admin(jwt);
        return service.limits();
    }

    @GetMapping("/status")
    public SystemStatus status(@AuthenticationPrincipal Jwt jwt) {
        admin(jwt);
        return status.status();
    }

    @GetMapping("/users")
    public PageResponse<AdminUser> users(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "ALL") UserFilter filter,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String sort) {
        return userService.page(admin(jwt), q, filter, page, size, sort);
    }

    @GetMapping("/users/{id}")
    public AdminUserDetail user(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return userService.detail(admin(jwt), id);
    }

    @PutMapping("/users/{id}/admin")
    public AdminUser setAdmin(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody AdminRequest request) {
        return userService.setAdmin(admin(jwt), id, request.admin());
    }

    @PutMapping("/users/{id}/blocked")
    public AdminUser setBlocked(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody BlockRequest request) {
        return userService.setBlocked(admin(jwt), id, request.blocked());
    }

    @DeleteMapping("/users/{id}/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSessions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        userService.revokeSessions(admin(jwt), id);
    }

    @GetMapping("/audit")
    public PageResponse<AuditResponse> audit(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        admin(jwt);
        return audit.page(page, size);
    }

    @GetMapping("/announcements")
    public PageResponse<AnnouncementResponse> announcements(@AuthenticationPrincipal Jwt jwt,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        admin(jwt);
        return announcements.page(page, size);
    }

    @PostMapping("/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public ActiveAnnouncement createAnnouncement(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AnnouncementRequest request) {
        return announcements.create(admin(jwt), request);
    }

    @DeleteMapping("/announcements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endAnnouncement(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        announcements.end(admin(jwt), id);
    }

    @PostMapping("/broadcast")
    public BroadcastResponse broadcast(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BroadcastRequest request) {
        return service.broadcast(admin(jwt), request);
    }

    /** Segunda barreira, caso a rota escape do filtro. */
    private UUID admin(Jwt jwt) {
        UUID id = CurrentUser.id(jwt);
        if (!users.isActiveAdmin(id)) throw new BusinessException(HttpStatus.FORBIDDEN, "Acesso restrito a administradores.");
        return id;
    }
}
