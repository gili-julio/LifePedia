package com.lifepedia.api.group;

import com.lifepedia.api.group.GroupDtos.CountResponse;
import com.lifepedia.api.group.GroupDtos.GroupSummary;
import com.lifepedia.api.group.GroupDtos.InviteCandidate;
import com.lifepedia.api.group.GroupDtos.InviteLinkResponse;
import com.lifepedia.api.group.GroupDtos.InviteRequest;
import com.lifepedia.api.group.GroupDtos.InviteResult;
import com.lifepedia.api.group.GroupDtos.LinkPreview;
import com.lifepedia.api.group.GroupDtos.ReceivedInvite;
import com.lifepedia.api.group.GroupDtos.SentInvite;
import com.lifepedia.api.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/groups")
public class GroupInviteController {

    private final GroupInviteService service;

    public GroupInviteController(GroupInviteService service) {
        this.service = service;
    }

    /** Usuários para convidar, por nome ou @username (mín. 2 caracteres). */
    @GetMapping("/{groupId}/invite-candidates")
    public List<InviteCandidate> candidates(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                            @RequestParam(defaultValue = "") String q) {
        return service.candidates(CurrentUser.id(jwt), groupId, q);
    }

    @PostMapping("/{groupId}/invites")
    public InviteResult invite(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                               @Valid @RequestBody InviteRequest request) {
        return service.invite(CurrentUser.id(jwt), groupId, request);
    }

    /** Convites pendentes enviados pelo grupo. */
    @GetMapping("/{groupId}/invites")
    public List<SentInvite> sent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        return service.sent(CurrentUser.id(jwt), groupId);
    }

    @DeleteMapping("/{groupId}/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID inviteId) {
        service.cancel(CurrentUser.id(jwt), groupId, inviteId);
    }

    /** Convites recebidos pendentes ("Solicitações"). */
    @GetMapping("/invites")
    public List<ReceivedInvite> received(@AuthenticationPrincipal Jwt jwt) {
        return service.received(CurrentUser.id(jwt));
    }

    @GetMapping("/invites/count")
    public CountResponse receivedCount(@AuthenticationPrincipal Jwt jwt) {
        return new CountResponse(service.receivedCount(CurrentUser.id(jwt)));
    }

    @PostMapping("/invites/{inviteId}/accept")
    public GroupSummary accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID inviteId) {
        return service.accept(CurrentUser.id(jwt), inviteId);
    }

    @PostMapping("/invites/{inviteId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID inviteId) {
        service.decline(CurrentUser.id(jwt), inviteId);
    }

    @GetMapping("/{groupId}/invite-link")
    public InviteLinkResponse link(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        return service.link(CurrentUser.id(jwt), groupId);
    }

    /** Gera um link novo, válido por 7 dias; o anterior para de funcionar. */
    @PostMapping("/{groupId}/invite-link")
    public InviteLinkResponse renewLink(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        return service.renewLink(CurrentUser.id(jwt), groupId);
    }

    @DeleteMapping("/{groupId}/invite-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeLink(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        service.revokeLink(CurrentUser.id(jwt), groupId);
    }

    @GetMapping("/join/{token}")
    public LinkPreview preview(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
        return service.preview(CurrentUser.id(jwt), token);
    }

    @GetMapping("/join/{token}/image")
    public ResponseEntity<byte[]> linkImage(@PathVariable String token) {
        return GroupController.imageResponse(service.linkImage(token));
    }

    @PostMapping("/join/{token}")
    public GroupSummary join(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
        return service.joinByLink(CurrentUser.id(jwt), token);
    }
}
