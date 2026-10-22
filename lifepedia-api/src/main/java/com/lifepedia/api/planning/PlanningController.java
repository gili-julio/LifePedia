package com.lifepedia.api.planning;

import com.lifepedia.api.planning.PlanningDtos.AcquireRequest;
import com.lifepedia.api.planning.PlanningDtos.ActiveRequest;
import com.lifepedia.api.planning.PlanningDtos.LinkPreview;
import com.lifepedia.api.planning.PlanningDtos.LinkPreviewRequest;
import com.lifepedia.api.planning.PlanningDtos.ReferenceLookup;
import com.lifepedia.api.planning.PlanningDtos.ReferenceRequest;
import com.lifepedia.api.planning.PlanningDtos.OrderRequest;
import com.lifepedia.api.planning.PlanningDtos.PlanItemRequest;
import com.lifepedia.api.planning.PlanningDtos.PlanItemResponse;
import com.lifepedia.api.planning.PlanningDtos.ScenarioComparison;
import com.lifepedia.api.planning.PlanningDtos.ScenarioRequest;
import com.lifepedia.api.planning.PlanningDtos.ScenarioResponse;
import com.lifepedia.api.planning.PlanningDtos.Settings;
import com.lifepedia.api.planning.PlanningDtos.SettingsRequest;
import com.lifepedia.api.planning.PlanningDtos.Simulation;
import com.lifepedia.api.shared.BusinessException;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Planejamento dentro de um grupo; tudo exige ser membro (senão 404). */
@RestController
@RequestMapping("/api/groups/{groupId}/planning")
public class PlanningController {

    private static final BigDecimal MAX_EXTRA = new BigDecimal("999999999");

    private final PlanningService service;
    private final PlanningSimulationService simulation;
    private final LinkPreviewService linkPreview;

    public PlanningController(PlanningService service, PlanningSimulationService simulation,
                              LinkPreviewService linkPreview) {
        this.service = service;
        this.simulation = simulation;
        this.linkPreview = linkPreview;
    }

    /**
     * Itens (todos, por prioridade) com o resultado de cada um, linha do tempo e totais.
     *
     * @param scenario    simula o cenário em vez dos itens ativos
     * @param extraIncome "e se": ganho extra por mês (não salvo)
     */
    @GetMapping
    public Simulation simulation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                 @RequestParam(required = false) UUID scenario,
                                 @RequestParam(required = false) BigDecimal extraIncome,
                                 @RequestParam(required = false) BigDecimal extraExpense) {
        return simulation.simulate(CurrentUser.id(jwt), groupId, scenario, extra(extraIncome), extra(extraExpense));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanItemResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                   @Valid @RequestBody PlanItemRequest request) {
        return service.create(CurrentUser.id(jwt), groupId, request);
    }

    @PutMapping("/items/{id}")
    public PlanItemResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id,
                                   @Valid @RequestBody PlanItemRequest request) {
        return service.update(CurrentUser.id(jwt), groupId, id, request);
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id) {
        service.delete(CurrentUser.id(jwt), groupId, id);
    }

    @PutMapping("/items/{id}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setActive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id,
                          @Valid @RequestBody ActiveRequest request) {
        service.setActive(CurrentUser.id(jwt), groupId, id, request.active());
    }

    @PutMapping("/items/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                        @Valid @RequestBody OrderRequest request) {
        service.reorder(CurrentUser.id(jwt), groupId, request.itemIds());
    }

    @PostMapping("/items/{id}/acquire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acquire(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id,
                        @Valid @RequestBody AcquireRequest request) {
        service.acquire(CurrentUser.id(jwt), groupId, id, request);
    }

    @DeleteMapping("/items/{id}/acquire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unacquire(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id) {
        service.unacquire(CurrentUser.id(jwt), groupId, id);
    }

    /** A URL leva {@code ?v=} (versão), então pode ficar em cache. */
    @GetMapping("/items/{id}/image")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id) {
        return service.image(CurrentUser.id(jwt), groupId, id).response();
    }

    @PutMapping("/settings")
    public Settings updateSettings(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                   @Valid @RequestBody SettingsRequest request) {
        return service.updateSettings(CurrentUser.id(jwt), groupId, request);
    }

    @GetMapping("/scenarios")
    public List<ScenarioResponse> scenarios(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        return service.scenarios(CurrentUser.id(jwt), groupId);
    }

    @GetMapping("/scenarios/compare")
    public List<ScenarioComparison> compare(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                            @RequestParam(required = false) BigDecimal extraIncome,
                                            @RequestParam(required = false) BigDecimal extraExpense) {
        return simulation.compare(CurrentUser.id(jwt), groupId, extra(extraIncome), extra(extraExpense));
    }

    @PostMapping("/scenarios")
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioResponse createScenario(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                           @Valid @RequestBody ScenarioRequest request) {
        return service.createScenario(CurrentUser.id(jwt), groupId, request);
    }

    @PutMapping("/scenarios/{id}")
    public ScenarioResponse updateScenario(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                           @PathVariable UUID id, @Valid @RequestBody ScenarioRequest request) {
        return service.updateScenario(CurrentUser.id(jwt), groupId, id, request);
    }

    @DeleteMapping("/scenarios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScenario(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id) {
        service.deleteScenario(CurrentUser.id(jwt), groupId, id);
    }

    @PostMapping("/scenarios/{id}/apply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void applyScenario(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id) {
        service.applyScenario(CurrentUser.id(jwt), groupId, id);
    }

    /** Lê nome, preço e foto do link (busca feita pela API, com proteção contra SSRF e limite por usuário). */
    @PostMapping("/link-preview")
    public LinkPreview linkPreview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                   @Valid @RequestBody LinkPreviewRequest request) {
        return linkPreview.preview(CurrentUser.id(jwt), groupId, request.url());
    }

    /** Preço e foto de produto parecido com o nome (Buscapé); mesmo limite por usuário da prévia de link. */
    @PostMapping("/price-reference")
    public ReferenceLookup priceReference(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                          @Valid @RequestBody ReferenceRequest request) {
        return new ReferenceLookup(linkPreview.referenceByName(CurrentUser.id(jwt), groupId, request.name()));
    }

    private static BigDecimal extra(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        if (value.signum() < 0 || value.compareTo(MAX_EXTRA) > 0 || value.scale() > 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Valor da simulação inválido.");
        }
        return value;
    }
}
