package com.lifepedia.api.search;

import java.util.List;
import java.util.Set;

/** Fonte da busca geral de um módulo. Cada módulo só devolve o que a pessoa pode ver. */
public interface SearchProvider {

    Set<SearchKind> kinds();

    /** Até {@code query.limit() + 1} itens (o extra indica que há mais), do mais relevante ao menos. */
    List<SearchHit> search(SearchQuery query, SearchKind kind);

    /** Contagem e soma de tudo o que a busca acha, para tipos com valor; nulo quando não se aplica. */
    default SearchTotals totals(SearchQuery query, SearchKind kind) {
        return null;
    }
}
