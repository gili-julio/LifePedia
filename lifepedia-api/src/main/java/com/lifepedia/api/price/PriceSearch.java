package com.lifepedia.api.price;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.search.SearchHit;
import com.lifepedia.api.search.SearchKind;
import com.lifepedia.api.search.SearchProvider;
import com.lifepedia.api.search.SearchQuery;
import com.lifepedia.api.search.SearchSql;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Itens com preço acompanhado (pessoais e dos grupos da pessoa) na busca geral. */
@Component
public class PriceSearch implements SearchProvider {

    private final JdbcClient jdbc;

    public PriceSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.PRICE_WATCH);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        String scope = query.groupId() != null ? "w.group_id = :groupId"
                : "((w.user_id = :userId and w.group_id is null) or w.group_id in (:groups))";
        return new SearchSql(query).where(scope)
                .text("w.name", List.of("w.name", "w.best_store"), List.of()).amounts("w.best_price")
                .list(jdbc, "w.id, w.name, w.best_price, w.best_store, g.name as group_name",
                        "price_watches w left join user_groups g on g.id = w.group_id", "w.updated_at", (rs, n) -> {
                            BigDecimal best = rs.getBigDecimal("best_price");
                            String store = rs.getString("best_store");
                            return SearchHit.of(SearchKind.PRICE_WATCH, SearchSql.uuid(rs, "id"), rs.getString("name"),
                                            "/precos/" + SearchSql.uuid(rs, "id"), rs.getDouble("score"))
                                    .subtitle(store == null ? "Preço acompanhado" : "Menor preço em " + store)
                                    .group(rs.getString("group_name"))
                                    .amount(best, best == null ? null : EntryType.EXPENSE)
                                    .field("Loja", store)
                                    .build();
                        });
    }
}
