package com.lifepedia.api.dev;

import com.lifepedia.api.search.SearchHit;
import com.lifepedia.api.search.SearchKind;
import com.lifepedia.api.search.SearchProvider;
import com.lifepedia.api.search.SearchQuery;
import com.lifepedia.api.search.SearchSql;
import com.lifepedia.api.search.SearchText;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Projetos do Dev, as issues e MRs sincronizadas e as anotações do dia na busca geral. */
@Component
public class DevSearch implements SearchProvider {

    private final JdbcClient jdbc;

    public DevSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.DEV_PROJECT, SearchKind.DEV_ITEM, SearchKind.DEV_REPORT);
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
        if (kind == SearchKind.DEV_REPORT) {
            return sql.where("r.user_id = :userId").text("p.name", List.of("p.name"), List.of("r.text")).dates("r.day")
                    .list(jdbc, "r.id, r.day, r.text, p.id as project_id, p.name as project",
                            "dev_reports r join dev_projects p on p.id = r.project_id", "r.day", (rs, n) -> report(rs, query));
        }
        return sql.where("i.user_id = :userId").where("i.kind in ('MR', 'ISSUE')")
                .text("i.title", List.of("i.title"), List.of("i.number", "i.branch", "i.labels", "i.author", "r.full_name"))
                .list(jdbc, "i.id, i.kind, i.number, i.title, i.state, i.branch, i.author, r.full_name as repo, "
                                + "p.id as project_id, p.name as project",
                        "dev_items i join dev_repos r on r.id = i.repo_id join dev_projects p on p.id = r.project_id",
                        "i.updated_at", this::item);
    }

    private static SearchHit report(ResultSet rs, SearchQuery query) throws SQLException {
        LocalDate day = SearchSql.date(rs, "day");
        return SearchHit.of(SearchKind.DEV_REPORT, SearchSql.uuid(rs, "id"), "Anotação de " + SearchHit.day(day),
                        "/dev/" + SearchSql.uuid(rs, "project_id") + "?aba=reports&dia=" + day, rs.getDouble("score"))
                .subtitle(rs.getString("project")).date(day).snippet(SearchText.snippet(rs.getString("text"), query.words()))
                .field("Projeto", rs.getString("project")).build();
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
