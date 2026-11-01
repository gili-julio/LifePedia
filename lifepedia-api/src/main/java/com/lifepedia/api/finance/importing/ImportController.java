package com.lifepedia.api.finance.importing;

import com.lifepedia.api.finance.importing.ImportDtos.AssistantConfig;
import com.lifepedia.api.finance.importing.ImportDtos.DraftContent;
import com.lifepedia.api.finance.importing.ImportDtos.DraftRequest;
import com.lifepedia.api.finance.importing.ImportDtos.DraftResponse;
import com.lifepedia.api.finance.importing.ImportDtos.ImportRequest;
import com.lifepedia.api.finance.importing.ImportDtos.ImportResponse;
import com.lifepedia.api.finance.importing.ImportDtos.PromptResponse;
import com.lifepedia.api.shared.CurrentUser;
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
@RequestMapping("/api/finance/import")
public class ImportController {

    private final ImportService service;
    private final ImportAssistantService assistant;
    private final ImportDraftService drafts;

    public ImportController(ImportService service, ImportAssistantService assistant, ImportDraftService drafts) {
        this.service = service;
        this.assistant = assistant;
        this.drafts = drafts;
    }

    @GetMapping("/assistant")
    public AssistantConfig assistant(@AuthenticationPrincipal Jwt jwt) {
        return assistant.config(CurrentUser.id(jwt));
    }

    @GetMapping("/prompt")
    public PromptResponse prompt(@AuthenticationPrincipal Jwt jwt) {
        return new PromptResponse(assistant.prompt(CurrentUser.id(jwt)));
    }

    /** Responde assim que a leitura começa; o resultado vira a revisão em {@code /draft}. */
    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DraftResponse analyze(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(name = "files", required = false) List<MultipartFile> files) {
        return assistant.analyze(CurrentUser.id(jwt), files == null ? List.of() : files);
    }

    @GetMapping("/draft")
    public ResponseEntity<DraftResponse> draft(@AuthenticationPrincipal Jwt jwt) {
        return drafts.current(CurrentUser.id(jwt)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/draft/{id}/content")
    public DraftContent content(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return drafts.content(CurrentUser.id(jwt), id);
    }

    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public DraftResponse createDraft(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DraftRequest request) {
        return drafts.createManual(CurrentUser.id(jwt), request);
    }

    @PutMapping("/draft/{id}")
    public DraftResponse saveDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody DraftRequest request) {
        return drafts.save(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/draft/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        drafts.discard(CurrentUser.id(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResponse importAll(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ImportRequest request) {
        return service.importAll(CurrentUser.id(jwt), request);
    }
}
