package com.lifepedia.api.attendance;

import com.lifepedia.api.search.SearchHit;
import com.lifepedia.api.search.SearchKind;
import com.lifepedia.api.search.SearchProvider;
import com.lifepedia.api.search.SearchQuery;
import com.lifepedia.api.search.SearchSql;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** Compromissos da frequência na busca geral. */
@Component
public class CommitmentSearch implements SearchProvider {

    private final JdbcClient jdbc;

    public CommitmentSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.COMMITMENT);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        return new SearchSql(query).where("c.user_id = :userId").text("c.name", List.of("c.name"), List.of())
                .list(jdbc, "c.id, c.name, c.start_time", "commitments c", "c.updated_at", (rs, n) -> SearchHit.of(
                                SearchKind.COMMITMENT, SearchSql.uuid(rs, "id"), rs.getString("name"), "/frequencia",
                                rs.getDouble("score"))
                        .subtitle("Compromisso às " + rs.getString("start_time").substring(0, 5)).build());
    }
}
