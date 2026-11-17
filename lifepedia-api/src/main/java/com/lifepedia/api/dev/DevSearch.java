package com.lifepedia.api.dev;

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
import java.util.Set;

/** Projetos do Dev e as issues e MRs sincronizadas na busca geral. */
@Component
public class DevSearch implements SearchProvider {

    private final JdbcClient jdbc;

    public DevSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.DEV_PROJECT, SearchKind.DEV_ITEM);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        SearchSql sql = new SearchSql(query);
        if (kind == SearchKind.DEV_PROJECT) {
            return sql.where("p.user_id = :userId").text("p.name", List.of("p.name"), List.of("p.description"))
                    .list(jdbc, "p.id, p.name, p.description", "dev_projects p", "p.updated_at", (rs, n) -> SearchHit.of(
                                    SearchKind.DEV_PROJECT, SearchSql.uuid(rs, "id"), rs.getString("name"),
                                    "/dev/" + SearchSql.uuid(rs, "id"), rs.getDouble("score"))
                            .subtitle("Projeto Dev").field("Descrição", rs.getString("description")).build());
        }
        return sql.where("i.user_id = :userId").where("i.kind in ('MR', 'ISSUE')")
                .text("i.title", List.of("i.title"), List.of("i.number", "i.branch", "i.labels", "i.author", "r.full_name"))
                .list(jdbc, "i.id, i.kind, i.number, i.title, i.state, i.branch, i.author, r.full_name as repo, "
                                + "p.id as project_id, p.name as project",
                        "dev_items i join dev_repos r on r.id = i.repo_id join dev_projects p on p.id = r.project_id",
                        "i.updated_at", this::item);
    }

    private SearchHit item(ResultSet rs, int row) throws SQLException {
        String kind = "MR".equals(rs.getString("kind")) ? "MR" : "Issue";
        String number = rs.getString("number");
        return SearchHit.of(SearchKind.DEV_ITEM, SearchSql.uuid(rs, "id"), rs.getString("title"),
                        "/dev/" + SearchSql.uuid(rs, "project_id"), rs.getDouble("score"))
                .subtitle(kind + (number == null ? "" : " " + number) + " · " + rs.getString("project"))
                .field("Repositório", rs.getString("repo")).field("Estado", rs.getString("state"))
                .field("Branch", rs.getString("branch")).field("Autor", rs.getString("author"))
                .build();
    }
}
