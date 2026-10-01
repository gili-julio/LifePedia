package com.lifepedia.api.shared;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Página de uma listagem ({@code page} começa em 0). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<List<E>, List<T>> mapper) {
        return new PageResponse<>(mapper.apply(page.getContent()), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
