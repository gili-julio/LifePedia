package com.lifepedia.api.group;

import com.lifepedia.api.finance.EntryType;
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
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Grupos da pessoa e o que é deles (lançamentos, fixos, planejamento, chat) na busca geral. Só de grupos onde é membro. */
@Component
public class GroupSearch implements SearchProvider {

    private final JdbcClient jdbc;

    public GroupSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.GROUP, SearchKind.GROUP_TRANSACTION, SearchKind.GROUP_RECURRING, SearchKind.PLAN_ITEM,
                SearchKind.MESSAGE);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        SearchSql sql = new SearchSql(query);
        return switch (kind) {
            case GROUP -> sql.inGroups("g.id").text("g.name", List.of("g.name"), List.of())
                    .list(jdbc, "g.id, g.name", "user_groups g", "g.last_activity_at", (rs, n) -> SearchHit.of(SearchKind.GROUP,
                            SearchSql.uuid(rs, "id"), rs.getString("name"), "/grupos/" + SearchSql.uuid(rs, "id"),
                            rs.getDouble("score")).subtitle("Grupo").build());
            case GROUP_TRANSACTION -> sql.inGroups("x.group_id")
                    .text("x.description", List.of("x.description", "c.name"), List.of("x.notes", "u.name"))
                    .dates("x.date").amounts("x.amount").finance("x.type", "x.category_id")
                    .list(jdbc, "x.id, x.group_id, g.name as group_name, x.description, x.type, x.amount, x.date, x.month, "
                                    + "c.name as category, u.name as author",
                            "group_transactions x join categories c on c.id = x.category_id "
                                    + "join user_groups g on g.id = x.group_id join users u on u.id = x.user_id",
                            "x.date", this::transaction);
            case GROUP_RECURRING -> sql.inGroups("x.group_id")
                    .text("x.description", List.of("x.description", "c.name"), List.of("x.notes", "u.name"))
                    .amounts("x.amount").finance("x.type", "x.category_id")
                    .list(jdbc, "x.id, x.group_id, g.name as group_name, x.description, x.type, x.amount, x.day_of_month, "
                                    + "x.active, c.name as category, u.name as author",
                            "group_recurring_entries x join categories c on c.id = x.category_id "
                                    + "join user_groups g on g.id = x.group_id join users u on u.id = x.user_id",
                            "x.updated_at", this::recurring);
            case PLAN_ITEM -> sql.inGroups("p.group_id")
                    .text("p.name", List.of("p.name", "p.link_title", "p.link_site"), List.of("p.notes"))
                    .dates("p.target_month").amounts("p.amount")
                    .list(jdbc, "p.id, p.group_id, g.name as group_name, p.name, p.amount, p.target_month, p.installments, "
                                    + "p.link_site, p.acquired_on",
                            "plan_items p join user_groups g on g.id = p.group_id", "p.updated_at", this::planItem);
            case MESSAGE -> sql.inGroups("m.group_id").where("m.deleted_at is null")
                    .text("m.body", List.of("m.body"), List.of("u.name")).dates("m.created_at::date")
                    .list(jdbc, "m.id, m.group_id, g.name as group_name, m.body, m.created_at::date as day, u.name as author",
                            "group_messages m join user_groups g on g.id = m.group_id join users u on u.id = m.user_id",
                            "m.created_at", (rs, n) -> message(rs, query));
            default -> List.of();
        };
    }

    private SearchHit transaction(ResultSet rs, int row) throws SQLException {
        UUID groupId = SearchSql.uuid(rs, "group_id");
        String description = rs.getString("description");
        LocalDate date = SearchSql.date(rs, "date");
        String category = rs.getString("category");
        return SearchHit.of(SearchKind.GROUP_TRANSACTION, SearchSql.uuid(rs, "id"), description,
                        "/grupos/" + groupId + "?aba=lancamentos&mes=" + YearMonth.from(SearchSql.date(rs, "month"))
                                + "&busca=" + SearchHit.encode(description), rs.getDouble("score"))
                .subtitle(category + " · " + SearchHit.day(date)).group(rs.getString("group_name")).date(date)
                .amount(rs.getBigDecimal("amount"), EntryType.valueOf(rs.getString("type")))
                .field("Categoria", category).field("Data", SearchHit.day(date))
                .field("Mês", SearchHit.month(SearchSql.date(rs, "month"))).field("Lançado por", rs.getString("author"))
                .build();
    }

    private SearchHit recurring(ResultSet rs, int row) throws SQLException {
        String description = rs.getString("description");
        Integer day = rs.getObject("day_of_month", Integer.class);
        String category = rs.getString("category");
        return SearchHit.of(SearchKind.GROUP_RECURRING, SearchSql.uuid(rs, "id"), description,
                        "/grupos/" + SearchSql.uuid(rs, "group_id") + "?aba=fixos&busca=" + SearchHit.encode(description),
                        rs.getDouble("score"))
                .subtitle(category + (day == null ? "" : " · todo dia " + day) + (rs.getBoolean("active") ? "" : " · inativo"))
                .group(rs.getString("group_name")).amount(rs.getBigDecimal("amount"), EntryType.valueOf(rs.getString("type")))
                .field("Categoria", category).field("Lançado por", rs.getString("author"))
                .build();
    }

    private SearchHit planItem(ResultSet rs, int row) throws SQLException {
        LocalDate month = SearchSql.date(rs, "target_month");
        int installments = rs.getInt("installments");
        LocalDate acquired = SearchSql.date(rs, "acquired_on");
        return SearchHit.of(SearchKind.PLAN_ITEM, SearchSql.uuid(rs, "id"), rs.getString("name"),
                        "/grupos/" + SearchSql.uuid(rs, "group_id") + "?aba=planejamento", rs.getDouble("score"))
                .subtitle("Planejamento · " + SearchHit.month(month)).group(rs.getString("group_name")).date(month)
                .amount(rs.getBigDecimal("amount"), EntryType.EXPENSE)
                .field("Mês", SearchHit.month(month)).field("Parcelas", installments > 1 ? installments + "x" : null)
                .field("Loja", rs.getString("link_site")).field("Comprado em", SearchHit.day(acquired))
                .build();
    }

    private static SearchHit message(ResultSet rs, SearchQuery query) throws SQLException {
        String author = rs.getString("author");
        LocalDate day = SearchSql.date(rs, "day");
        return SearchHit.of(SearchKind.MESSAGE, SearchSql.uuid(rs, "id"), "Mensagem de " + author,
                        "/grupos/" + SearchSql.uuid(rs, "group_id") + "?aba=chat", rs.getDouble("score"))
                .subtitle(SearchHit.day(day)).snippet(SearchText.snippet(rs.getString("body"), query.words()))
                .group(rs.getString("group_name")).date(day)
                .build();
    }
}
