package com.lifepedia.api.price;

import com.lifepedia.api.price.PriceDtos.CreateRequest;
import com.lifepedia.api.price.PriceDtos.LinkRequest;
import com.lifepedia.api.price.PriceDtos.ManualPriceRequest;
import com.lifepedia.api.price.PriceDtos.UpdateRequest;
import com.lifepedia.api.price.PriceDtos.WatchDetail;
import com.lifepedia.api.price.PriceDtos.WatchSummary;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final PriceWatchService service;

    public PriceController(PriceWatchService service) {
        this.service = service;
    }

    /** Sem {@code groupId}: os pessoais e os dos grupos da pessoa; com ele, só os do grupo. */
    @GetMapping
    public List<WatchSummary> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) UUID groupId) {
        return service.list(CurrentUser.id(jwt), groupId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchDetail create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/{id}")
    public WatchDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(CurrentUser.id(jwt), id);
    }

    @PutMapping("/{id}")
    public WatchDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return service.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/refresh")
    public WatchDetail refresh(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.refresh(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/links")
    public WatchDetail addLink(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody LinkRequest request) {
        return service.addLink(CurrentUser.id(jwt), id, request.url());
    }

    @DeleteMapping("/{id}/links/{linkId}")
    public WatchDetail removeLink(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID linkId) {
        return service.removeLink(CurrentUser.id(jwt), id, linkId);
    }

    /** Preço visto pela pessoa (na loja, no app ou pelo botão do navegador). */
    @PostMapping("/{id}/prices")
    public WatchDetail addPrice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody ManualPriceRequest request) {
        return service.addPrice(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}/prices/{pointId}")
    public WatchDetail removePrice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID pointId) {
        return service.removePrice(CurrentUser.id(jwt), id, pointId);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.image(CurrentUser.id(jwt), id).response();
    }
}
