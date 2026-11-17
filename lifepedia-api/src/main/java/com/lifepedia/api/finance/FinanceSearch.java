package com.lifepedia.api.finance;

import com.lifepedia.api.finance.bank.BankKind;
import com.lifepedia.api.finance.bank.BankRef;
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

/** Fixos, categorias, cartões e contas da pessoa na busca geral. */
@Component
public class FinanceSearch implements SearchProvider {

    private final JdbcClient jdbc;

    public FinanceSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.RECURRING, SearchKind.CATEGORY, SearchKind.CARD, SearchKind.ACCOUNT);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        SearchSql sql = new SearchSql(query);
        return switch (kind) {
            case RECURRING -> sql.where("r.user_id = :userId")
                    .text("r.description", List.of("r.description", "c.name"), List.of("r.notes", "r.bank_name", "r.bank_code"))
                    .amounts("r.amount").finance("r.type", "r.category_id")
                    .list(jdbc, "r.id, r.description, r.amount, r.type, r.day_of_month, r.active, r.start_month, r.end_month, "
                                    + "c.name as category, r.bank_code, r.bank_name, r.bank_kind",
                            "recurring_entries r join categories c on c.id = r.category_id", "r.updated_at", this::recurring);
            case CATEGORY -> sql.where("c.user_id = :userId").text("c.name", List.of("c.name"), List.of())
                    .list(jdbc, "c.id, c.name, c.type", "categories c", "c.updated_at", (rs, n) -> SearchHit.of(
                                    SearchKind.CATEGORY, SearchSql.uuid(rs, "id"), rs.getString("name"),
                                    "/financeiro/categorias?busca=" + SearchHit.encode(rs.getString("name")), rs.getDouble("score"))
                            .subtitle(EntryType.INCOME.name().equals(rs.getString("type")) ? "Categoria de entrada" : "Categoria de saída")
                            .build());
            case CARD -> sql.where("k.user_id = :userId")
                    .text("k.nickname", List.of("k.nickname", "k.bank_name", "k.bank_code"), List.of("k.last_digits"))
                    .list(jdbc, "k.id, k.nickname, k.bank_code, k.bank_name, k.last_digits, k.closing_day, k.due_day",
                            "finance_cards k", "k.updated_at", this::card);
            case ACCOUNT -> sql.where("a.user_id = :userId")
                    .text("a.nickname", List.of("a.nickname", "a.bank_name", "a.bank_code"), List.of())
                    .list(jdbc, "a.id, a.nickname, a.bank_code, a.bank_name", "finance_accounts a", "a.updated_at",
                            (rs, n) -> {
                                String bank = bank(rs, null);
                                String nickname = rs.getString("nickname");
                                return SearchHit.of(SearchKind.ACCOUNT, SearchSql.uuid(rs, "id"),
                                                nickname == null ? bank : nickname, "/financeiro/carteira", rs.getDouble("score"))
                                        .subtitle("Conta · " + bank).build();
                            });
            default -> List.of();
        };
    }

    private SearchHit recurring(ResultSet rs, int row) throws SQLException {
        String category = rs.getString("category");
        Integer day = rs.getObject("day_of_month", Integer.class);
        boolean active = rs.getBoolean("active");
        String description = rs.getString("description");
        return SearchHit.of(SearchKind.RECURRING, SearchSql.uuid(rs, "id"), description,
                        "/financeiro/fixos?busca=" + SearchHit.encode(description), rs.getDouble("score"))
                .subtitle(category + (day == null ? "" : " · todo dia " + day) + (active ? "" : " · inativo"))
                .amount(rs.getBigDecimal("amount"), EntryType.valueOf(rs.getString("type")))
                .field("Categoria", category).field("Desde", SearchHit.month(SearchSql.date(rs, "start_month")))
                .field("Até", SearchHit.month(SearchSql.date(rs, "end_month"))).field("Banco", bank(rs, rs.getString("bank_kind")))
                .build();
    }

    private SearchHit card(ResultSet rs, int row) throws SQLException {
        String bank = bank(rs, null);
        String nickname = rs.getString("nickname");
        String digits = rs.getString("last_digits");
        return SearchHit.of(SearchKind.CARD, SearchSql.uuid(rs, "id"), nickname == null ? bank : nickname,
                        "/financeiro/carteira/" + SearchSql.uuid(rs, "id"), rs.getDouble("score"))
                .subtitle("Cartão · " + bank + (digits == null ? "" : " · final " + digits))
                .field("Fecha", "dia " + rs.getInt("closing_day")).field("Vence", "dia " + rs.getInt("due_day"))
                .build();
    }

    private static String bank(ResultSet rs, String kind) throws SQLException {
        String code = rs.getString("bank_code");
        if (code == null) return null;
        String label = new BankRef(code, rs.getString("bank_name"), null).label();
        return BankKind.CARD.name().equals(kind) ? label + " (cartão)" : label;
    }
}
