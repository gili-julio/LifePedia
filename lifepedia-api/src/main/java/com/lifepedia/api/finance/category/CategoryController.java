package com.lifepedia.api.finance.category;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.category.CategoryDtos.CategoryRequest;
import com.lifepedia.api.finance.category.CategoryDtos.CategoryResponse;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    /** Página. {@code sort}: name, type ou usage, com {@code ,asc|desc}. */
    @GetMapping
    public PageResponse<CategoryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                               @RequestParam(required = false) EntryType type,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String sort) {
        return service.page(CurrentUser.id(jwt), type, page, size, sort);
    }

    /** Todas, sem paginação (seleção, prompt da IA). */
    @GetMapping("/all")
    public List<CategoryResponse> all(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) EntryType type) {
        return service.list(CurrentUser.id(jwt), type);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CategoryRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @Valid @RequestBody CategoryRequest request) {
        return service.update(CurrentUser.id(jwt), id, request);
    }

    /** @param replaceWith destino dos lançamentos, se a categoria estiver em uso */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                       @RequestParam(required = false) UUID replaceWith) {
        service.delete(CurrentUser.id(jwt), id, replaceWith);
    }
}
