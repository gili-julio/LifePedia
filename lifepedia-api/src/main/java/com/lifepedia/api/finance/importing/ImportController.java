package com.lifepedia.api.finance.importing;

import com.lifepedia.api.finance.importing.ImportDtos.ImportRequest;
import com.lifepedia.api.finance.importing.ImportDtos.ImportResponse;
import com.lifepedia.api.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/import")
public class ImportController {

    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResponse importAll(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ImportRequest request) {
        return service.importAll(CurrentUser.id(jwt), request);
    }
}
