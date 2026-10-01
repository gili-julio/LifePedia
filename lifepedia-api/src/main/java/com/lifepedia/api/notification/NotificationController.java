package com.lifepedia.api.notification;

import com.lifepedia.api.group.GroupDtos.CountResponse;
import com.lifepedia.api.notification.NotificationService.NotificationResponse;
import com.lifepedia.api.shared.CurrentUser;
import com.lifepedia.api.shared.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** Mais recentes primeiro; {@code size} até 50. */
    @GetMapping
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.page(CurrentUser.id(jwt), page, size);
    }

    @GetMapping("/unread-count")
    public CountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new CountResponse(service.unreadCount(CurrentUser.id(jwt)));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.markRead(CurrentUser.id(jwt), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(CurrentUser.id(jwt));
    }
}
