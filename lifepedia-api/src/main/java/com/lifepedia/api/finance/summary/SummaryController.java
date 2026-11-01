package com.lifepedia.api.finance.summary;

import com.lifepedia.api.finance.bank.BankDtos.BankOverview;
import com.lifepedia.api.finance.bank.BankFilter;
import com.lifepedia.api.finance.bank.BankKind;
import com.lifepedia.api.finance.summary.SummaryDtos.CategoryItem;
import com.lifepedia.api.finance.summary.SummaryDtos.MonthPoint;
import com.lifepedia.api.finance.summary.SummaryDtos.MonthlySummary;
import com.lifepedia.api.shared.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/finance/summary")
public class SummaryController {

    private final SummaryService service;

    public SummaryController(SummaryService service) {
        this.service = service;
    }

    /**
     * @param month {@code yyyy-MM}; padrão: mês atual
     * @param bank  só os itens do banco ({@code BankFilter})
     */
    @GetMapping
    public MonthlySummary monthly(@AuthenticationPrincipal Jwt jwt,
                                  @RequestParam(required = false) YearMonth month,
                                  @RequestParam(required = false) String bank,
                                  @RequestParam(required = false) BankKind bankKind) {
        return service.monthly(CurrentUser.id(jwt), month != null ? month : YearMonth.now(), BankFilter.of(bank, bankKind));
    }

    /** Gastos por banco no mês e nos {@code months} meses até ele. */
    @GetMapping("/banks")
    public BankOverview banks(@AuthenticationPrincipal Jwt jwt,
                              @RequestParam(required = false) YearMonth month,
                              @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months) {
        return service.banks(CurrentUser.id(jwt), month != null ? month : YearMonth.now(), months);
    }

    /** Itens da categoria no mês, agrupados pela descrição. */
    @GetMapping("/categories/{categoryId}")
    public List<CategoryItem> categoryItems(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID categoryId,
                                            @RequestParam(required = false) YearMonth month) {
        return service.categoryItems(CurrentUser.id(jwt), categoryId, month != null ? month : YearMonth.now());
    }

    @GetMapping("/history")
    public List<MonthPoint> history(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(required = false) YearMonth until,
                                    @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months) {
        return service.history(CurrentUser.id(jwt), until != null ? until : YearMonth.now(), months);
    }
}
