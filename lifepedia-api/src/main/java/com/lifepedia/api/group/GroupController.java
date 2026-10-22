package com.lifepedia.api.group;

import com.lifepedia.api.group.GroupDtos.GroupDetail;
import com.lifepedia.api.group.GroupDtos.GroupRequest;
import com.lifepedia.api.group.GroupDtos.GroupSummary;
import com.lifepedia.api.group.GroupDtos.RoleRequest;
import com.lifepedia.api.shared.CurrentUser;
import com.lifepedia.api.shared.image.Images;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @GetMapping
    public List<GroupSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupSummary create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GroupRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/{groupId}")
    public GroupDetail detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        return service.detail(CurrentUser.id(jwt), groupId);
    }

    @PutMapping("/{groupId}")
    public GroupSummary rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                               @Valid @RequestBody GroupRequest request) {
        return service.rename(CurrentUser.id(jwt), groupId, request);
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        service.delete(CurrentUser.id(jwt), groupId);
    }

    /** Multipart {@code file}: JPEG/PNG até 1 MB (o front envia já cortada em 512×512). */
    @PutMapping(value = "/{groupId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GroupSummary setImage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                 @RequestParam("file") MultipartFile file) {
        return service.setImage(CurrentUser.id(jwt), groupId, Images.uploadBytes(file));
    }

    @DeleteMapping("/{groupId}/image")
    public GroupSummary removeImage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        return service.removeImage(CurrentUser.id(jwt), groupId);
    }

    /** Membros e convidados. A URL leva {@code ?v=} (versão), então pode ficar em cache. */
    @GetMapping("/{groupId}/image")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        return service.image(CurrentUser.id(jwt), groupId).response();
    }

    @PostMapping("/{groupId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        service.leave(CurrentUser.id(jwt), groupId);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID userId) {
        service.removeMember(CurrentUser.id(jwt), groupId, userId);
    }

    @PutMapping("/{groupId}/members/{userId}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID userId,
                        @Valid @RequestBody RoleRequest request) {
        service.setRole(CurrentUser.id(jwt), groupId, userId, request.role());
    }
}
