package com.lifepedia.api.delivery;

import com.lifepedia.api.delivery.DeliveryDtos.AddressRequest;
import com.lifepedia.api.delivery.DeliveryDtos.AddressResponse;
import com.lifepedia.api.delivery.DeliveryDtos.ArchiveRequest;
import com.lifepedia.api.delivery.DeliveryDtos.CepResponse;
import com.lifepedia.api.delivery.DeliveryDtos.DeliveryRequest;
import com.lifepedia.api.delivery.DeliveryDtos.DeliveryResponse;
import com.lifepedia.api.delivery.DeliveryDtos.DeliveryUpdate;
import com.lifepedia.api.delivery.DeliveryDtos.Summary;
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
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeliveryResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "false") boolean archived) {
        return service.list(CurrentUser.id(jwt), archived);
    }

    @GetMapping("/summary")
    public Summary summary(@AuthenticationPrincipal Jwt jwt) {
        return service.summary(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeliveryRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/{id}")
    public DeliveryResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(CurrentUser.id(jwt), id);
    }

    @PutMapping("/{id}")
    public DeliveryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody DeliveryUpdate request) {
        return service.update(CurrentUser.id(jwt), id, request);
    }

    @PutMapping("/{id}/archive")
    public DeliveryResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ArchiveRequest request) {
        return service.archive(CurrentUser.id(jwt), id, request.archived());
    }

    @PostMapping("/{id}/refresh")
    public DeliveryResponse refresh(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.refresh(CurrentUser.id(jwt), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(CurrentUser.id(jwt), id);
    }

    /** Multipart {@code file}: JPEG/PNG até 1 MB (o front envia já recortada). */
    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DeliveryResponse setImage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return service.setImage(CurrentUser.id(jwt), id, Images.uploadBytes(file));
    }

    @DeleteMapping("/{id}/image")
    public DeliveryResponse removeImage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.removeImage(CurrentUser.id(jwt), id);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.image(CurrentUser.id(jwt), id).response();
    }

    @GetMapping("/addresses")
    public List<AddressResponse> addresses(@AuthenticationPrincipal Jwt jwt) {
        return service.addresses(CurrentUser.id(jwt));
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse createAddress(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
        return service.createAddress(CurrentUser.id(jwt), request);
    }

    @PutMapping("/addresses/{id}")
    public AddressResponse updateAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody AddressRequest request) {
        return service.updateAddress(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.deleteAddress(CurrentUser.id(jwt), id);
    }

    @GetMapping("/cep/{cep}")
    public CepResponse cep(@AuthenticationPrincipal Jwt jwt, @PathVariable String cep) {
        return service.cep(CurrentUser.id(jwt), cep);
    }
}
