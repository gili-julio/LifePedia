package com.lifepedia.api.financing;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.search.SearchHit;
import com.lifepedia.api.search.SearchKind;
import com.lifepedia.api.search.SearchProvider;
import com.lifepedia.api.search.SearchQuery;
import com.lifepedia.api.search.SearchSql;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Financiamentos da pessoa e dos grupos dela na busca geral. */
@Component
public class FinancingSearch implements SearchProvider {

    private static final Map<String, String> KIND = Map.of(
            "HOUSE", "Casa", "APARTMENT", "Apartamento", "CAR", "Carro", "MOTORCYCLE", "Moto", "OTHER", "Financiamento");

    private final JdbcClient jdbc;

    public FinancingSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.FINANCING);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        return new SearchSql(query).owned("f.user_id", "f.group_id")
                .text("f.name", List.of("f.name", "f.bank"), List.of("f.notes")).amounts("f.financed")
                .list(jdbc, "f.id, f.name, f.kind, f.financed, f.months, f.system, f.bank, f.first_month, f.group_id, "
                                + "g.name as group_name",
                        "financings f left join user_groups g on g.id = f.group_id", "f.updated_at", this::hit);
    }

    private SearchHit hit(ResultSet rs, int row) throws SQLException {
        UUID id = SearchSql.uuid(rs, "id");
        UUID groupId = SearchSql.uuid(rs, "group_id");
        String plan = rs.getInt("months") + "x " + rs.getString("system");
        return SearchHit.of(SearchKind.FINANCING, id, rs.getString("name"),
                        groupId == null ? "/financiamentos/" + id : "/grupos/" + groupId + "/financiamentos/" + id,
                        rs.getDouble("score"))
                .subtitle(KIND.getOrDefault(rs.getString("kind"), "Financiamento") + " · " + plan)
                .group(rs.getString("group_name")).amount(rs.getBigDecimal("financed"), EntryType.EXPENSE)
                .field("Plano", plan).field("Banco", rs.getString("bank"))
                .field("Primeira parcela", SearchHit.month(SearchSql.date(rs, "first_month")))
                .build();
    }
}
