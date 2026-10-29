package com.lifepedia.api.finance.group;

import com.lifepedia.api.finance.Installments.MoveRequest;
import com.lifepedia.api.finance.Installments.MoveResult;
import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.group.GroupFinanceDtos.GroupRecurringResponse;
import com.lifepedia.api.finance.group.GroupFinanceDtos.GroupSummary;
import com.lifepedia.api.finance.group.GroupFinanceDtos.GroupTransactionResponse;
import com.lifepedia.api.finance.group.GroupFinanceDtos.Origins;
import com.lifepedia.api.finance.group.GroupFinanceDtos.ShareRequest;
import com.lifepedia.api.finance.group.GroupFinanceDtos.ShareResult;
import com.lifepedia.api.finance.recurring.RecurringEntryDtos.RecurringEntryRequest;
import com.lifepedia.api.finance.summary.SummaryDtos.CategoryItem;
import com.lifepedia.api.finance.summary.SummaryDtos.MonthPoint;
import com.lifepedia.api.finance.transaction.TransactionDtos.TransactionRequest;
import com.lifepedia.api.shared.BusinessException;
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

/** Financeiro dentro de um grupo; tudo exige ser membro (senão 404). */
@RestController
@RequestMapping("/api/groups/{groupId}/finance")
public class GroupFinanceController {

    private final GroupFinanceService service;
    private final GroupSummaryService summaryService;

    public GroupFinanceController(GroupFinanceService service, GroupSummaryService summaryService) {
        this.service = service;
        this.summaryService = summaryService;
    }

    /** Leva ocasionais e fixos pessoais para o grupo ({@code mode}: SYNC ou COPY). */
    @PostMapping("/items")
    public ShareResult share(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                             @Valid @RequestBody ShareRequest request) {
        return service.share(CurrentUser.id(jwt), groupId, request);
    }

    /** Itens pessoais do usuário que já estão no grupo (para marcar no seletor). */
    @GetMapping("/origins")
    public Origins origins(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        return service.origins(CurrentUser.id(jwt), groupId);
    }

    /** {@code sort}: date (padrão, desc), description, category, member, amount. */
    @GetMapping("/transactions")
    public PageResponse<GroupTransactionResponse> transactions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                                               @RequestParam(required = false) YearMonth month,
                                                               @RequestParam(required = false) EntryType type,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "10") int size,
                                                               @RequestParam(required = false) String sort) {
        return service.transactions(CurrentUser.id(jwt), groupId, month != null ? month : YearMonth.now(), type,
                page, size, sort);
    }

    /** Lançamento só do grupo (sem item pessoal), com a categoria de quem cadastra. */
    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupTransactionResponse createTransaction(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                                      @Valid @RequestBody TransactionRequest request) {
        return service.createTransaction(CurrentUser.id(jwt), groupId, request);
    }

    @PutMapping("/transactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateTransaction(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id,
                                  @Valid @RequestBody TransactionRequest request) {
        service.updateTransaction(CurrentUser.id(jwt), groupId, id, request);
    }

    @PutMapping("/transactions/{id}/month")
    public MoveResult moveMonth(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id,
                                @Valid @RequestBody MoveRequest request) {
        return service.moveMonth(CurrentUser.id(jwt), groupId, id, request);
    }

    @DeleteMapping("/transactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTransaction(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id) {
        service.removeTransaction(CurrentUser.id(jwt), groupId, id);
    }

    /** {@code sort}: amount (padrão, desc), description, category, member, day, start. */
    @GetMapping("/recurring")
    public PageResponse<GroupRecurringResponse> recurring(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                                          @RequestParam(required = false) EntryType type,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam(required = false) String sort) {
        return service.recurring(CurrentUser.id(jwt), groupId, type, page, size, sort);
    }

    /** Fixo só do grupo (sem item pessoal). */
    @PostMapping("/recurring")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupRecurringResponse createRecurring(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                                  @Valid @RequestBody RecurringEntryRequest request) {
        return service.createRecurring(CurrentUser.id(jwt), groupId, request);
    }

    @PutMapping("/recurring/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateRecurring(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id,
                                @Valid @RequestBody RecurringEntryRequest request) {
        service.updateRecurring(CurrentUser.id(jwt), groupId, id, request);
    }

    @DeleteMapping("/recurring/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRecurring(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID id) {
        service.removeRecurring(CurrentUser.id(jwt), groupId, id);
    }

    @GetMapping("/summary")
    public GroupSummary summary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                @RequestParam(required = false) YearMonth month) {
        return summaryService.summary(CurrentUser.id(jwt), groupId, month != null ? month : YearMonth.now());
    }

    @GetMapping("/summary/history")
    public List<MonthPoint> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                    @RequestParam(required = false) YearMonth until,
                                    @RequestParam(defaultValue = "6") int months) {
        if (months < 1 || months > 24) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Informe entre 1 e 24 meses.");
        }
        return summaryService.history(CurrentUser.id(jwt), groupId, until != null ? until : YearMonth.now(), months);
    }

    /** @param category chave da categoria no resumo ({@code categoryId}, ex.: {@code EXPENSE:mercado}) */
    @GetMapping("/summary/category-items")
    public List<CategoryItem> categoryItems(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
                                            @RequestParam String category,
                                            @RequestParam(required = false) YearMonth month) {
        return summaryService.categoryItems(CurrentUser.id(jwt), groupId, category,
                month != null ? month : YearMonth.now());
    }
}
