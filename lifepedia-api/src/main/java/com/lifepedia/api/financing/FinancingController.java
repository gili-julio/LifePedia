package com.lifepedia.api.financing;

import com.lifepedia.api.financing.FinancingDtos.CostRequest;
import com.lifepedia.api.financing.FinancingDtos.ExtraPreview;
import com.lifepedia.api.financing.FinancingDtos.ExtraRequest;
import com.lifepedia.api.financing.FinancingDtos.FinancingRequest;
import com.lifepedia.api.financing.FinancingDtos.FinancingResponse;
import com.lifepedia.api.financing.FinancingDtos.FinancingSummary;
import com.lifepedia.api.financing.FinancingDtos.MyFixedRequest;
import com.lifepedia.api.financing.FinancingDtos.RateSuggestion;
import com.lifepedia.api.financing.FinancingDtos.SavedSimulation;
import com.lifepedia.api.financing.FinancingDtos.SimulationRequest;
import com.lifepedia.api.financing.FinancingDtos.SimulationResponse;
import com.lifepedia.api.financing.FinancingDtos.SimulationSaveRequest;
import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.NoGroupActivity;
import com.lifepedia.api.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

/** Financiamentos pessoais ({@code /api/financings}) e de grupo ({@code /api/groups/{groupId}/financings}); mesmas rotas. */
@RestController
@RequestMapping({"/api/financings", "/api/groups/{groupId}/financings"})
public class FinancingController {

    private final FinancingService service;
    private final FinancingSimulationService simulations;
    private final FinancingSimulator simulator;
    private final GroupAccess access;

    public FinancingController(FinancingService service, FinancingSimulationService simulations,
                               FinancingSimulator simulator, GroupAccess access) {
        this.service = service;
        this.simulations = simulations;
        this.simulator = simulator;
        this.access = access;
    }

    /** Nada é salvo; sem taxa informada usa as médias do Banco Central. */
    @PostMapping("/simulate")
    @NoGroupActivity
    public SimulationResponse simulate(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                       @Valid @RequestBody SimulationRequest request) {
        scope(jwt, groupId);
        return simulator.simulate(request);
    }

    @GetMapping("/rates")
    public RateSuggestion rates(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                @RequestParam FinancingKind kind) {
        scope(jwt, groupId);
        return simulator.suggestion(kind);
    }

    @GetMapping("/simulations")
    public List<SavedSimulation> simulations(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId) {
        return simulations.list(scope(jwt, groupId));
    }

    @PostMapping("/simulations")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedSimulation saveSimulation(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                          @Valid @RequestBody SimulationSaveRequest request) {
        return simulations.save(scope(jwt, groupId), request);
    }

    @DeleteMapping("/simulations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSimulation(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                 @PathVariable UUID id) {
        simulations.delete(scope(jwt, groupId), id);
    }

    @GetMapping
    public List<FinancingSummary> list(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId) {
        return service.list(scope(jwt, groupId));
    }

    /** Pessoal: todos de que a pessoa participa (inclusive de grupos); no grupo, igual à lista. */
    @GetMapping("/overview")
    public List<FinancingSummary> overview(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId) {
        FinancingScope scope = scope(jwt, groupId);
        return groupId == null ? service.overview(scope.userId()) : service.list(scope);
    }

    @GetMapping("/{id}")
    public FinancingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                 @PathVariable UUID id) {
        return service.get(scope(jwt, groupId), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinancingResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                    @Valid @RequestBody FinancingRequest request) {
        return service.create(scope(jwt, groupId), request);
    }

    @PutMapping("/{id}")
    public FinancingResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                    @PathVariable UUID id, @Valid @RequestBody FinancingRequest request) {
        return service.update(scope(jwt, groupId), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                       @PathVariable UUID id) {
        service.delete(scope(jwt, groupId), id);
    }

    @PostMapping("/{id}/extras/preview")
    @NoGroupActivity
    public ExtraPreview previewExtra(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                     @PathVariable UUID id, @Valid @RequestBody ExtraRequest request) {
        return service.previewExtra(scope(jwt, groupId), id, request);
    }

    @PostMapping("/{id}/extras")
    public FinancingResponse addExtra(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                      @PathVariable UUID id, @Valid @RequestBody ExtraRequest request) {
        return service.addExtra(scope(jwt, groupId), id, request);
    }

    @DeleteMapping("/{id}/extras/{extraId}")
    public FinancingResponse removeExtra(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                         @PathVariable UUID id, @PathVariable UUID extraId) {
        return service.removeExtra(scope(jwt, groupId), id, extraId);
    }

    @PostMapping("/{id}/costs")
    public FinancingResponse addCost(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                     @PathVariable UUID id, @Valid @RequestBody CostRequest request) {
        return service.addCost(scope(jwt, groupId), id, request);
    }

    @PutMapping("/{id}/costs/{costId}")
    public FinancingResponse updateCost(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                        @PathVariable UUID id, @PathVariable UUID costId,
                                        @Valid @RequestBody CostRequest request) {
        return service.updateCost(scope(jwt, groupId), id, costId, request);
    }

    @DeleteMapping("/{id}/costs/{costId}")
    public FinancingResponse removeCost(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                        @PathVariable UUID id, @PathVariable UUID costId) {
        return service.removeCost(scope(jwt, groupId), id, costId);
    }

    /** Liga/desliga o fixo pessoal com a parte de quem chama. */
    @PutMapping("/{id}/my-fixed")
    public FinancingResponse myFixed(@AuthenticationPrincipal Jwt jwt, @PathVariable(required = false) UUID groupId,
                                     @PathVariable UUID id, @Valid @RequestBody MyFixedRequest request) {
        return service.myFixed(scope(jwt, groupId), id, request);
    }

    private FinancingScope scope(Jwt jwt, UUID groupId) {
        UUID userId = CurrentUser.id(jwt);
        if (groupId == null) return FinancingScope.personal(userId);
        GroupMember member = access.requireMember(groupId, userId);
        return new FinancingScope(FinancingOwner.group(groupId), userId, member.isAdmin());
    }
}
