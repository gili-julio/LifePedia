package com.lifepedia.api.search;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.shared.BusinessException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Busca já validada.
 *
 * @param text     texto normalizado ({@link SearchText#normalize})
 * @param groupId  só o que é deste grupo; nulo = da pessoa e dos grupos dela
 * @param groupIds grupos visíveis (o do filtro, ou todos da pessoa)
 * @param limit    itens por tipo
 */
public record SearchQuery(UUID userId, String text, List<String> words, UUID groupId, List<UUID> groupIds,
                          LocalDate from, LocalDate to, BigDecimal min, BigDecimal max, EntryType type, UUID categoryId,
                          int limit) {

    public static final int MAX_TEXT = 100;

    /** Valida os filtros e normaliza o texto. */
    public static SearchQuery of(UUID userId, String q, UUID groupId, List<UUID> groupIds, LocalDate from, LocalDate to,
                                 BigDecimal min, BigDecimal max, EntryType type, UUID categoryId, int limit) {
        String raw = q == null ? "" : q;
        if (raw.length() > MAX_TEXT) throw new BusinessException(HttpStatus.BAD_REQUEST, "Busca muito longa.");
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Período inválido.");
        }
        if (min != null && max != null && max.compareTo(min) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Faixa de valor inválida.");
        }
        String text = SearchText.normalize(raw);
        return new SearchQuery(userId, text, SearchText.words(text), groupId, groupIds, from, to, min, max, type,
                categoryId, limit);
    }

    public boolean hasText() {
        return !words.isEmpty();
    }

    public boolean dated() {
        return from != null || to != null;
    }

    public boolean valued() {
        return min != null || max != null;
    }

    public boolean finance() {
        return type != null || categoryId != null;
    }

    public boolean filtered() {
        return dated() || valued() || finance();
    }

    /** O tipo atende todos os filtros e o escopo de grupo. */
    public boolean accepts(SearchKind kind) {
        return (!dated() || kind.dated()) && (!valued() || kind.valued()) && (!finance() || kind.finance())
                && (groupId == null || kind.group());
    }
}
