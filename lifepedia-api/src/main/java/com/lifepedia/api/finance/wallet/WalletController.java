package com.lifepedia.api.finance.wallet;

import com.lifepedia.api.finance.wallet.WalletDtos.AccountRequest;
import com.lifepedia.api.finance.wallet.WalletDtos.AccountView;
import com.lifepedia.api.finance.wallet.WalletDtos.BalanceRequest;
import com.lifepedia.api.finance.wallet.WalletDtos.CardDetail;
import com.lifepedia.api.finance.wallet.WalletDtos.CardRequest;
import com.lifepedia.api.finance.wallet.WalletDtos.GroupCard;
import com.lifepedia.api.finance.wallet.WalletDtos.PayRequest;
import com.lifepedia.api.finance.wallet.WalletDtos.PurchaseRequest;
import com.lifepedia.api.finance.wallet.WalletDtos.PurchaseResult;
import com.lifepedia.api.finance.wallet.WalletDtos.RecalculateItem;
import com.lifepedia.api.finance.wallet.WalletDtos.RecalculateRequest;
import com.lifepedia.api.finance.wallet.WalletDtos.RecalculateResult;
import com.lifepedia.api.finance.wallet.WalletDtos.Wallet;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
public class WalletController {

    private static final String BASE = "/api/finance/wallet";

    private final WalletService service;
    private final GroupCardsService groupCards;

    public WalletController(WalletService service, GroupCardsService groupCards) {
        this.service = service;
        this.groupCards = groupCards;
    }

    @GetMapping(BASE)
    public Wallet wallet(@AuthenticationPrincipal Jwt jwt) {
        return service.wallet(CurrentUser.id(jwt));
    }

    @PostMapping(BASE + "/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public CardDetail createCard(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CardRequest request) {
        return service.createCard(CurrentUser.id(jwt), request);
    }

    /** @param month fatura ({@code yyyy-MM}, mês do vencimento); padrão: a aberta */
    @GetMapping(BASE + "/cards/{id}")
    public CardDetail card(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(required = false) YearMonth month) {
        return service.card(CurrentUser.id(jwt), id, month);
    }

    @PutMapping(BASE + "/cards/{id}")
    public CardDetail updateCard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody CardRequest request) {
        return service.updateCard(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping(BASE + "/cards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.deleteCard(CurrentUser.id(jwt), id);
    }

    @PutMapping(BASE + "/cards/{id}/invoices/{month}/paid")
    public CardDetail pay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable YearMonth month,
                          @Valid @RequestBody PayRequest request) {
        return service.pay(CurrentUser.id(jwt), id, month, request);
    }

    @DeleteMapping(BASE + "/cards/{id}/invoices/{month}/paid")
    public CardDetail unpay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable YearMonth month) {
        return service.unpay(CurrentUser.id(jwt), id, month);
    }

    @GetMapping(BASE + "/cards/{id}/recalculate")
    public List<RecalculateItem> recalculatePreview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                    @RequestParam YearMonth from) {
        return service.recalculatePreview(CurrentUser.id(jwt), id, from);
    }

    @PostMapping(BASE + "/cards/{id}/recalculate")
    public RecalculateResult recalculate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody RecalculateRequest request) {
        return service.recalculate(CurrentUser.id(jwt), id, request);
    }

    @PostMapping(BASE + "/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView createAccount(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AccountRequest request) {
        return service.createAccount(CurrentUser.id(jwt), request);
    }

    @PutMapping(BASE + "/accounts/{id}")
    public AccountView updateAccount(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody AccountRequest request) {
        return service.updateAccount(CurrentUser.id(jwt), id, request);
    }

    @PutMapping(BASE + "/accounts/{id}/balance")
    public AccountView setBalance(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody BalanceRequest request) {
        return service.setBalance(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping(BASE + "/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.deleteAccount(CurrentUser.id(jwt), id);
    }

    @PostMapping(BASE + "/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResult purchase(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PurchaseRequest request) {
        return service.purchase(CurrentUser.id(jwt), request);
    }

    /** Cartões que os membros mostram no grupo. */
    @GetMapping("/api/groups/{groupId}/finance/cards")
    public List<GroupCard> groupCards(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        return groupCards.cards(CurrentUser.id(jwt), groupId);
    }
}
