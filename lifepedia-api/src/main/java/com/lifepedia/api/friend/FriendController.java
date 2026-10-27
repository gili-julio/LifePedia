package com.lifepedia.api.friend;

import com.lifepedia.api.friend.FriendDtos.Candidate;
import com.lifepedia.api.friend.FriendDtos.CountResponse;
import com.lifepedia.api.friend.FriendDtos.FriendProfile;
import com.lifepedia.api.friend.FriendDtos.FriendRequest;
import com.lifepedia.api.friend.FriendDtos.FriendRequestResult;
import com.lifepedia.api.friend.FriendDtos.FriendResponse;
import com.lifepedia.api.friend.FriendDtos.Requests;
import com.lifepedia.api.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService service;

    public FriendController(FriendService service) {
        this.service = service;
    }

    /** Lista completa (limitada a 200 amigos), por nome. */
    @GetMapping
    public List<FriendResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt));
    }

    @GetMapping("/candidates")
    public List<Candidate> candidates(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "") String q) {
        return service.candidates(CurrentUser.id(jwt), q);
    }

    @GetMapping("/requests")
    public Requests requests(@AuthenticationPrincipal Jwt jwt) {
        return service.requests(CurrentUser.id(jwt));
    }

    @GetMapping("/requests/count")
    public CountResponse receivedCount(@AuthenticationPrincipal Jwt jwt) {
        return new CountResponse(service.receivedCount(CurrentUser.id(jwt)));
    }

    @PostMapping("/requests")
    public FriendRequestResult request(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FriendRequest request) {
        return service.request(CurrentUser.id(jwt), request.userId());
    }

    @PostMapping("/requests/{requestId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        service.accept(CurrentUser.id(jwt), requestId);
    }

    /** Recusa (recebido) ou cancela (enviado). */
    @DeleteMapping("/requests/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        service.dismiss(CurrentUser.id(jwt), requestId);
    }

    @GetMapping("/{friendId}")
    public FriendProfile profile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID friendId) {
        return service.profile(CurrentUser.id(jwt), friendId);
    }

    @DeleteMapping("/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID friendId) {
        service.remove(CurrentUser.id(jwt), friendId);
    }
}
