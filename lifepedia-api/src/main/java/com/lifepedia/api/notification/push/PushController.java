package com.lifepedia.api.notification.push;

import com.lifepedia.api.auth.TokenService;
import com.lifepedia.api.notification.push.PushDtos.PushConfig;
import com.lifepedia.api.notification.push.PushDtos.SubscriptionRequest;
import com.lifepedia.api.notification.push.PushDtos.SubscriptionResponse;
import com.lifepedia.api.notification.push.PushDtos.UnsubscribeRequest;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService service;

    public PushController(PushService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public PushConfig config() {
        return new PushConfig(service.publicKey());
    }

    /** Liga o aparelho à sessão atual (logout desliga; entrar de novo religa com {@code auto}). */
    @PutMapping("/subscription")
    public SubscriptionResponse subscribe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubscriptionRequest request) {
        String sid = jwt.getClaimAsString(TokenService.SESSION_CLAIM);
        if (sid == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "Entre de novo para ativar as notificações.");
        UUID sessionId = UUID.fromString(sid);
        return new SubscriptionResponse(service.subscribe(CurrentUser.id(jwt), sessionId, request));
    }

    @DeleteMapping("/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UnsubscribeRequest request) {
        service.unsubscribe(CurrentUser.id(jwt), request.endpoint());
    }
}
