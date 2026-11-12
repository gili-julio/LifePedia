package com.lifepedia.api.finance.simulation;

import com.lifepedia.api.finance.simulation.SimulatorDtos.CompareItem;
import com.lifepedia.api.finance.simulation.SimulatorDtos.CompareRequest;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Input;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Result;
import com.lifepedia.api.finance.simulation.SimulatorDtos.SaveRequest;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Saved;
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

@RestController
@RequestMapping("/api/finance/simulator")
public class SimulatorController {

    private final SimulatorService service;

    public SimulatorController(SimulatorService service) {
        this.service = service;
    }

    @PostMapping("/run")
    public Result run(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Input input,
                      @RequestParam(defaultValue = "false") boolean real) {
        return service.run(CurrentUser.id(jwt), input, real);
    }

    @PostMapping("/compare")
    public List<CompareItem> compare(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CompareRequest request,
                                     @RequestParam(defaultValue = "false") boolean real) {
        return service.compare(CurrentUser.id(jwt), request.ids(), real);
    }

    @GetMapping("/saved")
    public List<Saved> saved(@AuthenticationPrincipal Jwt jwt) {
        return service.saved(CurrentUser.id(jwt));
    }

    @PostMapping("/saved")
    @ResponseStatus(HttpStatus.CREATED)
    public Saved save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveRequest request) {
        return service.save(CurrentUser.id(jwt), request);
    }

    @PutMapping("/saved/{id}")
    public Saved update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody SaveRequest request) {
        return service.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/saved/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(CurrentUser.id(jwt), id);
    }
}
