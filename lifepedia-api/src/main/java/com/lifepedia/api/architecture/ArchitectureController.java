package com.lifepedia.api.architecture;

import com.lifepedia.api.architecture.ArchCollabService.AttachmentFile;
import com.lifepedia.api.architecture.ArchitectureDtos.AttachmentResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.CommentRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.CommentResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.CurrentRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.LightRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.LightResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.LinkRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.LinkResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.NewVersionRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.PendingVersion;
import com.lifepedia.api.architecture.ArchitectureDtos.ProjectDetail;
import com.lifepedia.api.architecture.ArchitectureDtos.ProjectList;
import com.lifepedia.api.architecture.ArchitectureDtos.ProjectRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.ResolvedRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.RoleRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.RoomRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.RoomResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.RoomUpdate;
import com.lifepedia.api.architecture.ArchitectureDtos.Scene;
import com.lifepedia.api.architecture.ArchitectureDtos.ShareOptions;
import com.lifepedia.api.architecture.ArchitectureDtos.Shares;
import com.lifepedia.api.architecture.ArchitectureDtos.VariantRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.VariantResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.VariantUpdate;
import com.lifepedia.api.architecture.ArchitectureDtos.VersionResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.VersionUpdate;
import com.lifepedia.api.architecture.ArchitectureDtos.ViewRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.ViewResponse;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.CurrentUser;
import com.lifepedia.api.shared.image.Images;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/architecture")
public class ArchitectureController {

    static final String GLB = "model/gltf-binary";

    private final ArchitectureService service;
    private final ArchShareService sharing;
    private final ArchCollabService collab;
    private final ArchSceneService scene;

    public ArchitectureController(ArchitectureService service, ArchShareService sharing, ArchCollabService collab,
                                  ArchSceneService scene) {
        this.service = service;
        this.sharing = sharing;
        this.collab = collab;
        this.scene = scene;
    }

    @GetMapping("/projects")
    public ProjectList list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt));
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDetail create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProjectRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/projects/{id}")
    public ProjectDetail detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.detail(id, CurrentUser.id(jwt));
    }

    @PutMapping("/projects/{id}")
    public ProjectDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ProjectRequest request) {
        return service.update(id, CurrentUser.id(jwt), request);
    }

    @DeleteMapping("/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CurrentUser.id(jwt));
    }

    @PutMapping("/projects/{id}/cover")
    public Map<String, String> cover(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam MultipartFile file) {
        return Map.of("coverUrl", service.cover(id, CurrentUser.id(jwt), Images.uploadBytes(file)));
    }

    @GetMapping("/projects/{id}/cover")
    public ResponseEntity<Resource> coverFile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return file(service.coverFile(id, CurrentUser.id(jwt)), MediaType.IMAGE_JPEG, Duration.ofDays(30));
    }

    @PostMapping("/projects/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public PendingVersion startVersion(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       @Valid @RequestBody NewVersionRequest request) {
        return service.startVersion(id, CurrentUser.id(jwt), request);
    }

    /** Corpo = o GLB inteiro (não multipart): vai direto para o disco, sem passar pela memória. */
    @PutMapping(value = "/versions/{id}/model", consumes = {GLB, MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public VersionResponse uploadModel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest request)
            throws IOException {
        return service.uploadModel(id, CurrentUser.id(jwt), request.getInputStream());
    }

    @GetMapping("/versions/{id}/model")
    public ResponseEntity<Resource> model(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return file(service.model(id, CurrentUser.id(jwt)), MediaType.parseMediaType(GLB), Duration.ofDays(365));
    }

    @PutMapping("/versions/{id}")
    public VersionResponse updateVersion(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody VersionUpdate request) {
        return service.updateVersion(id, CurrentUser.id(jwt), request);
    }

    @DeleteMapping("/versions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVersion(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.deleteVersion(id, CurrentUser.id(jwt));
    }

    @PutMapping("/projects/{id}/current")
    public ProjectDetail setCurrent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody CurrentRequest request) {
        return service.setCurrent(id, CurrentUser.id(jwt), request);
    }

    @GetMapping("/projects/{id}/comments")
    public List<CommentResponse> comments(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam UUID versionId) {
        return collab.comments(id, CurrentUser.id(jwt), versionId);
    }

    @PostMapping("/projects/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse comment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @Valid @RequestBody CommentRequest request) {
        return collab.comment(id, CurrentUser.id(jwt), request);
    }

    @PutMapping("/projects/{id}/comments/{commentId}/resolved")
    public CommentResponse resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID commentId,
                                   @Valid @RequestBody ResolvedRequest request) {
        return collab.resolve(id, CurrentUser.id(jwt), commentId, request.resolved());
    }

    @DeleteMapping("/projects/{id}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID commentId) {
        collab.deleteComment(id, CurrentUser.id(jwt), commentId);
    }

    @PostMapping("/projects/{id}/views")
    @ResponseStatus(HttpStatus.CREATED)
    public ViewResponse addView(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ViewRequest request) {
        return collab.addView(id, CurrentUser.id(jwt), request);
    }

    @DeleteMapping("/projects/{id}/views/{viewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteView(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID viewId) {
        collab.deleteView(id, CurrentUser.id(jwt), viewId);
    }

    @GetMapping("/projects/{id}/scene")
    public Scene scene(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam UUID versionId) {
        return scene.scene(id, CurrentUser.id(jwt), versionId);
    }

    @PostMapping("/projects/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public VariantResponse addVariant(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @Valid @RequestBody VariantRequest request) {
        return scene.addVariant(id, CurrentUser.id(jwt), request);
    }

    @PutMapping("/projects/{id}/variants/{variantId}")
    public VariantResponse updateVariant(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID variantId,
                                         @Valid @RequestBody VariantUpdate request) {
        return scene.updateVariant(id, CurrentUser.id(jwt), variantId, request);
    }

    @DeleteMapping("/projects/{id}/variants/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVariant(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID variantId) {
        scene.deleteVariant(id, CurrentUser.id(jwt), variantId);
    }

    @PostMapping("/projects/{id}/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse addRoom(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody RoomRequest request) {
        return scene.addRoom(id, CurrentUser.id(jwt), request);
    }

    @PutMapping("/projects/{id}/rooms/{roomId}")
    public RoomResponse renameRoom(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID roomId,
                                   @Valid @RequestBody RoomUpdate request) {
        return scene.renameRoom(id, CurrentUser.id(jwt), roomId, request);
    }

    @DeleteMapping("/projects/{id}/rooms/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoom(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID roomId) {
        scene.deleteRoom(id, CurrentUser.id(jwt), roomId);
    }

    @PostMapping("/projects/{id}/lights")
    @ResponseStatus(HttpStatus.CREATED)
    public LightResponse addLight(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody LightRequest request) {
        return scene.addLight(id, CurrentUser.id(jwt), request);
    }

    @DeleteMapping("/projects/{id}/lights/{lightId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLight(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID lightId) {
        scene.deleteLight(id, CurrentUser.id(jwt), lightId);
    }

    @GetMapping("/projects/{id}/attachments")
    public List<AttachmentResponse> attachments(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return collab.attachments(id, CurrentUser.id(jwt));
    }

    @PostMapping("/projects/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse attach(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam MultipartFile file,
                                     @RequestParam(required = false) String name) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo.");
        }
        return collab.attach(id, CurrentUser.id(jwt), name == null || name.isBlank() ? file.getOriginalFilename() : name, bytes);
    }

    /** PDF abre no navegador, em sandbox (sem script nem acesso à sessão). */
    @GetMapping("/projects/{id}/attachments/{attachmentId}/file")
    public ResponseEntity<Resource> attachmentFile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                   @PathVariable UUID attachmentId) {
        AttachmentFile found = collab.attachmentFile(id, CurrentUser.id(jwt), attachmentId);
        boolean pdf = found.attachment().getKind() == ArchAttachment.Kind.PDF;
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(found.attachment().getName(), StandardCharsets.UTF_8).build().toString())
                .header("Content-Security-Policy", "sandbox")
                .header("X-Content-Type-Options", "nosniff")
                .body(found.resource());
    }

    @DeleteMapping("/projects/{id}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID attachmentId) {
        collab.deleteAttachment(id, CurrentUser.id(jwt), attachmentId);
    }

    @GetMapping("/projects/{id}/shares")
    public Shares shares(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return sharing.list(id, CurrentUser.id(jwt));
    }

    @GetMapping("/projects/{id}/share-options")
    public ShareOptions shareOptions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return sharing.options(id, CurrentUser.id(jwt));
    }

    @PutMapping("/projects/{id}/shares/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void shareWithUser(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId,
                              @Valid @RequestBody RoleRequest request) {
        sharing.shareWithUser(id, CurrentUser.id(jwt), userId, request.role());
    }

    @DeleteMapping("/projects/{id}/shares/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unshareUser(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId) {
        sharing.unshareUser(id, CurrentUser.id(jwt), userId);
    }

    @DeleteMapping("/projects/{id}/shares/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        sharing.leave(id, CurrentUser.id(jwt));
    }

    @PutMapping("/projects/{id}/shares/groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void shareWithGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID groupId,
                               @Valid @RequestBody RoleRequest request) {
        sharing.shareWithGroup(id, CurrentUser.id(jwt), groupId, request.role());
    }

    @DeleteMapping("/projects/{id}/shares/groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unshareGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID groupId) {
        sharing.unshareGroup(id, CurrentUser.id(jwt), groupId);
    }

    @PostMapping("/projects/{id}/links")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkResponse createLink(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody LinkRequest request) {
        return sharing.createLink(id, CurrentUser.id(jwt), request.days());
    }

    @DeleteMapping("/projects/{id}/links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLink(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID linkId) {
        sharing.deleteLink(id, CurrentUser.id(jwt), linkId);
    }

    /** Arquivo com nome aleatório (muda a cada versão/capa), então pode ficar em cache privado. */
    static ResponseEntity<Resource> file(Resource resource, MediaType type, Duration maxAge) {
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(maxAge).cachePrivate())
                .eTag(resource.getFilename())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
