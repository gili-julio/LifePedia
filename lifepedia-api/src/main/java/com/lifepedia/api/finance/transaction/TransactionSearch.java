package com.lifepedia.api.finance.transaction;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.bank.BankFilter;
import com.lifepedia.api.finance.bank.BankKind;
import com.lifepedia.api.finance.bank.BankRef;
import com.lifepedia.api.search.SearchHit;
import com.lifepedia.api.search.SearchKind;
import com.lifepedia.api.search.SearchProvider;
import com.lifepedia.api.search.SearchQuery;
import com.lifepedia.api.search.SearchSql;
import com.lifepedia.api.search.SearchTotals;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lançamentos da pessoa na busca geral e na busca da tela de lançamentos. */
@Component
public class TransactionSearch implements SearchProvider {

    private static final String FROM = "transactions t join categories c on c.id = t.category_id";
    private static final String TOTALS = "count(*) as total, "
            + "sum(case when t.type = 'INCOME' then t.amount else 0 end) as income, "
            + "sum(case when t.type = 'EXPENSE' then t.amount else 0 end) as expense";

    private final JdbcClient jdbc;

    public TransactionSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.TRANSACTION);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        return sql(query, BankFilter.ALL).list(jdbc, "t.id, t.description, t.original_description, t.amount, t.type, "
                + "t.date, t.month, c.name as category, t.bank_code, t.bank_name, t.bank_kind", FROM, "t.date", this::hit);
    }

    @Override
    public SearchTotals totals(SearchQuery query, SearchKind kind) {
        return totals(query, BankFilter.ALL);
    }

    SearchTotals totals(SearchQuery query, BankFilter bank) {
        return sql(query, bank).aggregate(jdbc, TOTALS, FROM, (rs, n) -> new SearchTotals(rs.getLong("total"),
                SearchSql.money(rs, "income"), SearchSql.money(rs, "expense")));
    }

    /** Ids da página. @param order expressão já validada (com {@code t} e {@code c}) */
    List<UUID> ids(SearchQuery query, BankFilter bank, String order, int page, int size) {
        return sql(query, bank).page(jdbc, "t.id", FROM, order + ", t.created_at desc, t.id", size, page * size,
                (rs, n) -> SearchSql.uuid(rs, "id"));
    }

    private SearchSql sql(SearchQuery query, BankFilter bank) {
        SearchSql sql = new SearchSql(query).where("t.user_id = :userId")
                .text("t.description", List.of("t.description", "t.original_description", "c.name"),
                        List.of("t.notes", "t.bank_name", "t.bank_code"))
                .dates("t.date").amounts("t.amount").finance("t.type", "t.category_id");
        if (bank.none()) return sql.where("t.bank_code is null");
        if (bank.code() != null) sql.where("t.bank_code = :bankCode").param("bankCode", bank.code());
        if (bank.name() != null) sql.where("lower(trim(t.bank_name)) = :bankName").param("bankName", bank.name());
        if (bank.kind() != null) sql.where("t.bank_kind = :bankKind").param("bankKind", bank.kind().name());
        return sql;
    }

    private SearchHit hit(ResultSet rs, int row) throws SQLException {
        UUID id = SearchSql.uuid(rs, "id");
        LocalDate date = SearchSql.date(rs, "date");
        LocalDate month = SearchSql.date(rs, "month");
        String category = rs.getString("category");
        String bankCode = rs.getString("bank_code");
        String kind = rs.getString("bank_kind");
        String bank = bankCode == null ? null : new BankRef(bankCode, rs.getString("bank_name"),
                kind == null ? null : BankKind.valueOf(kind)).label() + (BankKind.CARD.name().equals(kind) ? " (cartão)" : "");
        return SearchHit.of(SearchKind.TRANSACTION, id, rs.getString("description"),
                        "/financeiro/lancamentos?mes=" + YearMonth.from(month) + "&lancamento=" + id, rs.getDouble("score"))
                .subtitle(category + " · " + SearchHit.day(date))
                .date(date).amount(rs.getBigDecimal("amount"), EntryType.valueOf(rs.getString("type")))
                .field("Categoria", category).field("Data", SearchHit.day(date)).field("Mês", SearchHit.month(month))
                .field("Banco", bank).field("No extrato", rs.getString("original_description"))
                .build();
    }
}
