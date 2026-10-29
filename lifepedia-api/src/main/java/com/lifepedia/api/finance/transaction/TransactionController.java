package com.lifepedia.api.finance.transaction;

import com.lifepedia.api.finance.Installments.MoveRequest;
import com.lifepedia.api.finance.Installments.MoveResult;
import com.lifepedia.api.finance.transaction.TransactionDtos.TransactionBatchRequest;
import com.lifepedia.api.finance.transaction.TransactionDtos.TransactionRequest;
import com.lifepedia.api.finance.transaction.TransactionDtos.TransactionResponse;
import com.lifepedia.api.finance.EntryType;
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
@RequestMapping("/api/finance/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    /**
     * Página do mês. {@code sort}: date, description, category ou amount, com {@code ,asc|desc}.
     *
     * @param month {@code yyyy-MM}; padrão: mês atual
     */
    @GetMapping
    public PageResponse<TransactionResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam(required = false) YearMonth month,
                                                  @RequestParam(required = false) EntryType type,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) String sort) {
        return service.page(CurrentUser.id(jwt), month != null ? month : YearMonth.now(), type, page, size, sort);
    }

    /** Todos do período, sem paginação (duplicados na importação, tornar fixo). {@code month} = from = to. */
    @GetMapping("/all")
    public List<TransactionResponse> all(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(required = false) YearMonth month,
                                         @RequestParam(required = false) YearMonth from,
                                         @RequestParam(required = false) YearMonth to,
                                         @RequestParam(required = false) EntryType type,
                                         @RequestParam(required = false) String description) {
        YearMonth start = month != null ? month : from != null ? from : YearMonth.now();
        YearMonth end = month != null ? month : to != null ? to : start.plusYears(100);
        return service.search(CurrentUser.id(jwt), start, end, type, description);
    }

    /** Descrições mais usadas nos últimos 12 meses (até 200), para o prompt de importação. */
    @GetMapping("/descriptions")
    public List<String> descriptions(@AuthenticationPrincipal Jwt jwt) {
        return service.descriptions(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody TransactionRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TransactionResponse> createBatch(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody TransactionBatchRequest request) {
        return service.createBatch(CurrentUser.id(jwt), request.items());
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @Valid @RequestBody TransactionRequest request) {
        return service.update(CurrentUser.id(jwt), id, request);
    }

    /** Adianta/adia a parcela ({@code following}: e as seguintes). */
    @PutMapping("/{id}/month")
    public MoveResult moveMonth(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody MoveRequest request) {
        return service.moveMonth(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(CurrentUser.id(jwt), id);
    }
}
