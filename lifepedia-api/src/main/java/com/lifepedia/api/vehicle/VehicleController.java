package com.lifepedia.api.vehicle;

import com.lifepedia.api.shared.CurrentUser;
import com.lifepedia.api.shared.image.Images;
import com.lifepedia.api.vehicle.VehicleDtos.ArchiveRequest;
import com.lifepedia.api.vehicle.VehicleDtos.FipeOption;
import com.lifepedia.api.vehicle.VehicleDtos.FipePrice;
import com.lifepedia.api.vehicle.VehicleDtos.ShareOptions;
import com.lifepedia.api.vehicle.VehicleDtos.ShareRequest;
import com.lifepedia.api.vehicle.VehicleDtos.Shares;
import com.lifepedia.api.vehicle.VehicleDtos.Summary;
import com.lifepedia.api.vehicle.VehicleDtos.Template;
import com.lifepedia.api.vehicle.VehicleDtos.VehicleDetail;
import com.lifepedia.api.vehicle.VehicleDtos.VehicleList;
import com.lifepedia.api.vehicle.VehicleDtos.VehicleRequest;
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

/** Veículos: cadastro, foto, FIPE, tabela sugerida e compartilhamento. */
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService service;
    private final VehicleShareService sharing;

    public VehicleController(VehicleService service, VehicleShareService sharing) {
        this.service = service;
        this.sharing = sharing;
    }

    @GetMapping
    public VehicleList list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt));
    }

    @GetMapping("/summary")
    public Summary summary(@AuthenticationPrincipal Jwt jwt) {
        return service.summary(CurrentUser.id(jwt));
    }

    @GetMapping("/templates")
    public List<Template> templates(@RequestParam VehicleType type) {
        return service.templates(type);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleDetail create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VehicleRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/{id}")
    public VehicleDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(CurrentUser.id(jwt), id);
    }

    @PutMapping("/{id}")
    public VehicleDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody VehicleRequest request) {
        return service.update(CurrentUser.id(jwt), id, request);
    }

    @PutMapping("/{id}/archive")
    public VehicleDetail archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ArchiveRequest request) {
        return service.archive(CurrentUser.id(jwt), id, request.archived());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(CurrentUser.id(jwt), id);
    }

    /** Multipart {@code file}: JPEG/PNG até 1 MB (o front envia já recortada). */
    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VehicleDetail setImage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return service.setImage(CurrentUser.id(jwt), id, Images.uploadBytes(file));
    }

    @DeleteMapping("/{id}/image")
    public VehicleDetail removeImage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.removeImage(CurrentUser.id(jwt), id);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.image(CurrentUser.id(jwt), id).response();
    }

    @PostMapping("/{id}/fipe/refresh")
    public VehicleDetail refreshFipe(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.refreshFipe(CurrentUser.id(jwt), id);
    }

    @GetMapping("/fipe/{kind}/brands")
    public List<FipeOption> fipeBrands(@AuthenticationPrincipal Jwt jwt, @PathVariable FipeKind kind) {
        return service.fipeBrands(CurrentUser.id(jwt), kind);
    }

    @GetMapping("/fipe/{kind}/brands/{brand}/models")
    public List<FipeOption> fipeModels(@AuthenticationPrincipal Jwt jwt, @PathVariable FipeKind kind, @PathVariable String brand) {
        return service.fipeModels(CurrentUser.id(jwt), kind, brand);
    }

    @GetMapping("/fipe/{kind}/brands/{brand}/models/{model}/years")
    public List<FipeOption> fipeYears(@AuthenticationPrincipal Jwt jwt, @PathVariable FipeKind kind, @PathVariable String brand,
                                      @PathVariable String model) {
        return service.fipeYears(CurrentUser.id(jwt), kind, brand, model);
    }

    @GetMapping("/fipe/{kind}/brands/{brand}/models/{model}/years/{year}")
    public FipePrice fipePrice(@AuthenticationPrincipal Jwt jwt, @PathVariable FipeKind kind, @PathVariable String brand,
                               @PathVariable String model, @PathVariable String year) {
        return service.fipePrice(CurrentUser.id(jwt), kind, brand, model, year);
    }

    @GetMapping("/{id}/shares")
    public Shares shares(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return sharing.list(id, CurrentUser.id(jwt));
    }

    @GetMapping("/{id}/share-options")
    public ShareOptions shareOptions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return sharing.options(id, CurrentUser.id(jwt));
    }

    @PutMapping("/{id}/shares/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void shareWithUser(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId,
                              @Valid @RequestBody ShareRequest request) {
        sharing.shareWithUser(id, CurrentUser.id(jwt), userId, request.role());
    }

    @DeleteMapping("/{id}/shares/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unshareUser(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId) {
        sharing.unshareUser(id, CurrentUser.id(jwt), userId);
    }

    @DeleteMapping("/{id}/shares/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        sharing.leave(id, CurrentUser.id(jwt));
    }

    @PutMapping("/{id}/shares/groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void shareWithGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID groupId,
                               @Valid @RequestBody ShareRequest request) {
        sharing.shareWithGroup(id, CurrentUser.id(jwt), groupId, request.role());
    }

    @DeleteMapping("/{id}/shares/groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unshareGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID groupId) {
        sharing.unshareGroup(id, CurrentUser.id(jwt), groupId);
    }
}
