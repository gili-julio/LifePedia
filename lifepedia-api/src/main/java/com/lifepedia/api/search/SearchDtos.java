package com.lifepedia.api.search;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.user.AppModule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SearchDtos {

    private SearchDtos() {
    }

    /**
     * @param modules só destes módulos (vazio = todos os visíveis)
     * @param kinds   só destes tipos (vazio = todos)
     * @param groupId só o que é deste grupo
     * @param limit   itens por tipo (1 a 20; padrão 5)
     */
    public record SearchRequest(String q, Set<AppModule> modules, Set<SearchKind> kinds, UUID groupId, LocalDate from,
                                LocalDate to, BigDecimal min, BigDecimal max, EntryType type, UUID categoryId,
                                Integer limit) {
    }

    /** @param more há mais itens deste tipo além dos mostrados */
    public record SearchGroup(SearchKind kind, AppModule module, List<SearchHit> items, boolean more) {
    }

    /**
     * @param groups do tipo com o resultado mais relevante ao menos
     * @param totals contagem e soma dos lançamentos achados (nulo sem lançamentos)
     */
    public record SearchResponse(List<SearchGroup> groups, SearchTotals totals) {

        public static final SearchResponse EMPTY = new SearchResponse(List.of(), null);
    }
}
