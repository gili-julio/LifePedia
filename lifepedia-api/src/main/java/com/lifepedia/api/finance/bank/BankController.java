package com.lifepedia.api.finance.bank;

import com.lifepedia.api.finance.bank.BankDtos.AssignRequest;
import com.lifepedia.api.finance.bank.BankDtos.AssignResult;
import com.lifepedia.api.finance.bank.BankDtos.LastBank;
import com.lifepedia.api.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/banks")
public class BankController {

    private final BankService service;

    public BankController(BankService service) {
        this.service = service;
    }

    /** Último banco usado, para o formulário já abrir com ele. */
    @GetMapping("/last")
    public LastBank last(@AuthenticationPrincipal Jwt jwt) {
        return service.last(CurrentUser.id(jwt));
    }

    /** Define (ou tira) o banco de vários ocasionais e fixos pessoais. */
    @PutMapping("/assign")
    public AssignResult assign(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AssignRequest request) {
        return service.assign(CurrentUser.id(jwt), request);
    }
}
