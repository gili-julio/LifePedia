package com.lifepedia.api.note;

import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.note.NoteDtos.Activity;
import com.lifepedia.api.note.NoteDtos.ArchiveRequest;
import com.lifepedia.api.note.NoteDtos.Folder;
import com.lifepedia.api.note.NoteDtos.FolderRequest;
import com.lifepedia.api.note.NoteDtos.ImageResponse;
import com.lifepedia.api.note.NoteDtos.Labels;
import com.lifepedia.api.note.NoteDtos.LinkOptions;
import com.lifepedia.api.note.NoteDtos.NoteRef;
import com.lifepedia.api.note.NoteDtos.NoteRequest;
import com.lifepedia.api.note.NoteDtos.NoteResponse;
import com.lifepedia.api.note.NoteDtos.NoteSummary;
import com.lifepedia.api.note.NoteDtos.PinRequest;
import com.lifepedia.api.note.NoteDtos.Status;
import com.lifepedia.api.note.NoteDtos.Tag;
import com.lifepedia.api.note.NoteDtos.TagRequest;
import com.lifepedia.api.shared.CurrentUser;
import com.lifepedia.api.shared.PageResponse;
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

/** Notas pessoais ({@code /api/notes}) e de grupo ({@code /api/groups/{groupId}/notes}, só membros); mesmas rotas. */
@RestController
@RequestMapping({"/api/notes", "/api/groups/{groupId}/notes"})
public class NoteController {

    private final NoteService service;
    private final NoteLabelService labels;
    private final GroupAccess access;

    public NoteController(NoteService service, NoteLabelService labels, GroupAccess access) {
        this.service = service;
        this.labels = labels;
        this.access = access;
    }

    /** {@code sort}: {@code updatedAt} (padrão, mais nova primeiro), {@code createdAt} ou {@code title}; fixadas sempre antes. */
    @GetMapping
    public PageResponse<NoteSummary> list(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                          @RequestParam(defaultValue = "ACTIVE") Status status,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) UUID folderId,
                                          @RequestParam(required = false) UUID tagId,
                                          @RequestParam(required = false) UUID taskId,
                                          @RequestParam(required = false) UUID commitmentId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String sort) {
        return service.list(scope(jwt, groupId), status, q, folderId, tagId, taskId, commitmentId, page, size, sort);
    }

    @GetMapping("/all")
    public List<NoteRef> all(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId) {
        return service.refs(scope(jwt, groupId).owner());
    }

    @GetMapping("/labels")
    public Labels labels(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId) {
        return labels.labels(scope(jwt, groupId).owner());
    }

    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public Folder createFolder(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                               @Valid @RequestBody FolderRequest request) {
        return labels.createFolder(scope(jwt, groupId).owner(), request);
    }

    @PutMapping("/folders/{id}")
    public Folder renameFolder(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                               @PathVariable UUID id, @Valid @RequestBody FolderRequest request) {
        return labels.renameFolder(scope(jwt, groupId).owner(), id, request);
    }

    @DeleteMapping("/folders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                             @PathVariable UUID id) {
        labels.deleteFolder(scope(jwt, groupId).owner(), id);
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public Tag createTag(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                         @Valid @RequestBody TagRequest request) {
        return labels.createTag(scope(jwt, groupId).owner(), request);
    }

    @PutMapping("/tags/{id}")
    public Tag updateTag(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                         @PathVariable UUID id, @Valid @RequestBody TagRequest request) {
        return labels.updateTag(scope(jwt, groupId).owner(), id, request);
    }

    @DeleteMapping("/tags/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTag(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                          @PathVariable UUID id) {
        labels.deleteTag(scope(jwt, groupId).owner(), id);
    }

    @GetMapping("/link-options")
    public LinkOptions linkOptions(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId) {
        return service.linkOptions(scope(jwt, groupId));
    }

    @GetMapping("/{id}")
    public NoteResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                            @PathVariable UUID id) {
        return service.get(scope(jwt, groupId), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                               @Valid @RequestBody NoteRequest request) {
        return service.create(scope(jwt, groupId), request);
    }

    @PutMapping("/{id}")
    public NoteResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                               @PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        return service.update(scope(jwt, groupId), id, request);
    }

    @PutMapping("/{id}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pin(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                    @PathVariable UUID id, @Valid @RequestBody PinRequest request) {
        service.pin(scope(jwt, groupId), id, request.pinned());
    }

    @PutMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                        @PathVariable UUID id, @Valid @RequestBody ArchiveRequest request) {
        service.archive(scope(jwt, groupId), id, request.archived());
    }

    /** Vai para a lixeira. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trash(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                      @PathVariable UUID id) {
        service.trash(scope(jwt, groupId), id);
    }

    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                        @PathVariable UUID id) {
        service.restore(scope(jwt, groupId), id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermanently(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                  @PathVariable UUID id) {
        service.deletePermanently(scope(jwt, groupId), id);
    }

    @GetMapping("/{id}/activity")
    public List<Activity> activity(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                   @PathVariable UUID id) {
        return service.activity(scope(jwt, groupId), id);
    }

    /** Multipart {@code file}: JPEG/PNG até 1 MB (o front reduz antes de enviar). */
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageResponse addImage(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                  @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return service.addImage(scope(jwt, groupId), id, Images.uploadBytes(file));
    }

    @GetMapping("/{id}/images/{key}")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                        @PathVariable UUID id, @PathVariable String key) {
        return service.image(scope(jwt, groupId), id, key).response();
    }

    private NoteScope scope(Jwt jwt, UUID groupId) {
        UUID userId = CurrentUser.id(jwt);
        if (groupId == null) return NoteScope.personal(userId);
        GroupMember member = access.requireMember(groupId, userId);
        return new NoteScope(NoteOwner.group(groupId), userId, member.isAdmin());
    }
}
