package com.lifepedia.api.architecture;

import com.lifepedia.api.search.SearchHit;
import com.lifepedia.api.search.SearchKind;
import com.lifepedia.api.search.SearchProvider;
import com.lifepedia.api.search.SearchQuery;
import com.lifepedia.api.search.SearchSql;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** Projetos de arquitetura da pessoa e os compartilhados com ela (ou com os grupos dela) na busca geral. */
@Component
public class ArchitectureSearch implements SearchProvider {

    private final JdbcClient jdbc;

    public ArchitectureSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.ARCH_PROJECT);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        return new SearchSql(query).where("(a.user_id = :userId or exists (select 1 from arch_shares s "
                        + "where s.project_id = a.id and (s.user_id = :userId or s.group_id in (:groups))))")
                .text("a.name", List.of("a.name"), List.of("a.description"))
                .list(jdbc, "a.id, a.name, a.description, a.user_id = :userId as mine", "arch_projects a", "a.updated_at",
                        (rs, n) -> SearchHit.of(SearchKind.ARCH_PROJECT, SearchSql.uuid(rs, "id"), rs.getString("name"),
                                        "/arquitetura/" + SearchSql.uuid(rs, "id"), rs.getDouble("score"))
                                .subtitle(rs.getBoolean("mine") ? "Projeto de arquitetura" : "Projeto compartilhado com você")
                                .field("Descrição", rs.getString("description")).build());
    }
}
