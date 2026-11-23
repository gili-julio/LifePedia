package com.lifepedia.api.school;

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

/** Matérias da Escola na busca geral (nome, código, professor, sala). */
@Component
public class SchoolSearch implements SearchProvider {

    private final JdbcClient jdbc;

    public SchoolSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.SCHOOL_COURSE);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        return new SearchSql(query).where("c.user_id = :userId")
                .text("c.name", List.of("c.name", "c.code", "c.teacher"), List.of("c.room", "e.course"))
                .list(jdbc, "c.id, c.name, c.code, c.period, c.status, c.teacher, c.room, e.course",
                        "school_courses c join school_enrollments e on e.id = c.enrollment_id", "c.updated_at",
                        SchoolSearch::course);
    }

    private static SearchHit course(ResultSet rs, int row) throws SQLException {
        String code = rs.getString("code");
        String period = rs.getString("period");
        return SearchHit.of(SearchKind.SCHOOL_COURSE, SearchSql.uuid(rs, "id"), rs.getString("name"),
                        "/escola/materias/" + SearchSql.uuid(rs, "id"), rs.getDouble("score"))
                .subtitle((code == null ? "" : code + " · ") + (period == null ? "" : period + " · ") + rs.getString("course"))
                .field("Situação", rs.getString("status")).field("Professor", rs.getString("teacher"))
                .field("Sala", rs.getString("room")).build();
    }
}
