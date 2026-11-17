package com.lifepedia.api.task;

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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tarefas e hábitos da pessoa e dos grupos dela na busca geral. */
@Component
public class TaskSearch implements SearchProvider {

    private static final Map<String, String> PRIORITY = Map.of("LOW", "Baixa", "MEDIUM", "Média", "HIGH", "Alta");

    private final JdbcClient jdbc;

    public TaskSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.TASK);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        return new SearchSql(query).owned("t.user_id", "t.group_id")
                .text("t.title", List.of("t.title", "l.name"), List.of("t.notes")).dates("t.due_date")
                .list(jdbc, "t.id, t.title, t.notes, t.group_id, g.name as group_name, t.due_date, t.due_time, t.habit, "
                                + "t.completed_at is not null as done, t.priority, l.name as list_name",
                        "tasks t left join task_lists l on l.id = t.list_id left join user_groups g on g.id = t.group_id",
                        "t.updated_at", (rs, n) -> hit(rs, query));
    }

    private static SearchHit hit(ResultSet rs, SearchQuery query) throws SQLException {
        UUID id = SearchSql.uuid(rs, "id");
        UUID groupId = SearchSql.uuid(rs, "group_id");
        LocalDate due = SearchSql.date(rs, "due_date");
        String list = rs.getString("list_name");
        String status = rs.getBoolean("done") ? "Concluída" : rs.getBoolean("habit") ? "Hábito"
                : due == null ? "Sem data" : "Para " + SearchHit.day(due);
        String time = rs.getString("due_time");
        return SearchHit.of(SearchKind.TASK, id, rs.getString("title"),
                        groupId == null ? "/tarefas?tarefa=" + id : "/grupos/" + groupId + "?aba=tarefas&tarefa=" + id,
                        rs.getDouble("score"))
                .subtitle(list == null ? status : status + " · " + list).group(rs.getString("group_name")).date(due)
                .snippet(SearchText.snippet(rs.getString("notes"), query.words()))
                .field("Situação", status).field("Lista", list).field("Data", SearchHit.day(due))
                .field("Hora", time == null ? null : time.substring(0, 5)).field("Prioridade", PRIORITY.get(rs.getString("priority")))
                .build();
    }
}
