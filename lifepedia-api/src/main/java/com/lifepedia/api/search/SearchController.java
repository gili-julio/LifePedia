package com.lifepedia.api.search;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.search.SearchDtos.SearchRequest;
import com.lifepedia.api.search.SearchDtos.SearchResponse;
import com.lifepedia.api.shared.CurrentUser;
import com.lifepedia.api.user.AppModule;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    /** Busca em tudo o que a pessoa vê; filtros opcionais. */
    @GetMapping("/api/search")
    public SearchResponse search(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(required = false) Set<AppModule> modules,
                                 @RequestParam(required = false) Set<SearchKind> kinds,
                                 @RequestParam(required = false) UUID groupId,
                                 @RequestParam(required = false) LocalDate from,
                                 @RequestParam(required = false) LocalDate to,
                                 @RequestParam(required = false) BigDecimal min,
                                 @RequestParam(required = false) BigDecimal max,
                                 @RequestParam(required = false) EntryType type,
                                 @RequestParam(required = false) UUID categoryId,
                                 @RequestParam(required = false) Integer limit) {
        return service.search(CurrentUser.id(jwt),
                new SearchRequest(q, modules, kinds, groupId, from, to, min, max, type, categoryId, limit));
    }
}
