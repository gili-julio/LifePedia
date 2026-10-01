package com.lifepedia.api.finance.recurring;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.recurring.RecurringEntryDtos.AdjustmentRequest;
import com.lifepedia.api.finance.recurring.RecurringEntryDtos.AdjustmentResponse;
import com.lifepedia.api.finance.recurring.RecurringEntryDtos.RecurringEntryRequest;
import com.lifepedia.api.finance.recurring.RecurringEntryDtos.RecurringEntryResponse;
import com.lifepedia.api.shared.CurrentUser;
import com.lifepedia.api.shared.PageResponse;
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

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/recurring")
public class RecurringEntryController {

    private final RecurringEntryService service;

    public RecurringEntryController(RecurringEntryService service) {
        this.service = service;
    }

    /** Página. {@code sort}: description, category, day, start ou amount, com {@code ,asc|desc}. */
    @GetMapping
    public PageResponse<RecurringEntryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(required = false) EntryType type,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size,
                                                     @RequestParam(required = false) String sort) {
        return service.page(CurrentUser.id(jwt), type, page, size, sort);
    }

    /** Todos, sem paginação (seleção e importação). */
    @GetMapping("/all")
    public List<RecurringEntryResponse> all(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(required = false) EntryType type) {
        return service.list(CurrentUser.id(jwt), type);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringEntryResponse create(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody RecurringEntryRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @PutMapping("/{id}")
    public RecurringEntryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody RecurringEntryRequest request) {
        return service.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(CurrentUser.id(jwt), id);
    }

    @PutMapping("/{id}/adjustments/{month}")
    public AdjustmentResponse adjust(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable YearMonth month,
                                     @Valid @RequestBody AdjustmentRequest request) {
        return service.adjust(CurrentUser.id(jwt), id, month, request.amount());
    }

    @DeleteMapping("/{id}/adjustments/{month}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAdjustment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable YearMonth month) {
        service.removeAdjustment(CurrentUser.id(jwt), id, month);
    }
}
