package com.lifepedia.api.note;

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
import java.util.UUID;

/** Notas da pessoa e dos grupos dela (fora da lixeira) na busca geral, com o trecho do texto onde achou. */
@Component
public class NoteSearch implements SearchProvider {

    private final JdbcClient jdbc;

    public NoteSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.NOTE);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        return new SearchSql(query).owned("n.user_id", "n.group_id").where("n.deleted_at is null")
                .text("n.title", List.of("n.title", "f.name"), List.of("n.content")).dates("n.updated_at::date")
                .list(jdbc, "n.id, n.title, n.group_id, g.name as group_name, left(n.content, 20000) as content, "
                                + "n.updated_at::date as day, n.archived_at is not null as archived, n.template, f.name as folder",
                        "notes n left join note_folders f on f.id = n.folder_id left join user_groups g on g.id = n.group_id",
                        "n.updated_at", (rs, n) -> note(rs, query));
    }

    private static SearchHit note(ResultSet rs, SearchQuery query) throws SQLException {
        UUID id = SearchSql.uuid(rs, "id");
        UUID groupId = SearchSql.uuid(rs, "group_id");
        LocalDate day = SearchSql.date(rs, "day");
        String folder = rs.getString("folder");
        String state = rs.getBoolean("template") ? "Modelo" : rs.getBoolean("archived") ? "Arquivada" : null;
        String title = rs.getString("title");
        StringBuilder subtitle = new StringBuilder("Editada em ").append(SearchHit.day(day));
        if (folder != null) subtitle.append(" · ").append(folder);
        if (state != null) subtitle.append(" · ").append(state);
        return SearchHit.of(SearchKind.NOTE, id, title.isBlank() ? "Sem título" : title,
                        groupId == null ? "/notas/" + id : "/grupos/" + groupId + "/notas/" + id, rs.getDouble("score"))
                .subtitle(subtitle.toString()).group(rs.getString("group_name")).date(day)
                .snippet(SearchText.snippet(rs.getString("content"), query.words()))
                .field("Pasta", folder).field("Situação", state).field("Editada em", SearchHit.day(day))
                .build();
    }
}
