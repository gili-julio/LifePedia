package com.lifepedia.api.shared;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** Paginação e ordenação das listagens: {@code page}, {@code size} (1 a 100) e {@code sort=campo,asc|desc}. */
public final class Paging {

    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    /**
     * @param fields campo público → expressão JPQL (lista fechada)
     * @param tieBreakers desempate estável, na mesma direção
     */
    public static Pageable pageable(int page, int size, String sort, Map<String, String> fields,
                                    String defaultSort, String... tieBreakers) {
        if (page < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Página inválida.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Itens por página: de 1 a " + MAX_SIZE + ".");
        }
        String[] parts = (sort == null || sort.isBlank() ? defaultSort : sort).split(",");
        String expression = fields.get(parts[0].trim());
        Sort.Direction direction = parts.length > 1 ? Sort.Direction.fromOptionalString(parts[1].trim()).orElse(null)
                : Sort.Direction.ASC;
        if (expression == null || direction == null || parts.length > 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ordenação inválida.");
        }
        return PageRequest.of(page, size, JpaSort.unsafe(direction, expression).andUnsafe(direction, tieBreakers));
    }
}
