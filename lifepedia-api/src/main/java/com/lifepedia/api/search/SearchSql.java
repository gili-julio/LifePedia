package com.lifepedia.api.search;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monta a consulta de um tipo da busca: cada palavra precisa aparecer (sem acento) em algum campo, ou ser parecida
 * (trigramas) com os campos curtos. A relevância soma a semelhança com o texto todo e um bônus quando o título começa
 * com ele. Filtros só entram quando informados. A consulta expõe a coluna {@code score}.
 */
public final class SearchSql {

    private static final double SIMILAR = 0.3;
    private static final int FUZZY_FROM = 5;
    private static final UUID NO_GROUP = new UUID(0, 0);

    private final SearchQuery query;
    private final List<String> conditions = new ArrayList<>();
    private final Map<String, Object> params = new HashMap<>();
    private String score = "0";

    public SearchSql(SearchQuery query) {
        this.query = query;
        params.put("userId", query.userId());
        params.put("groups", query.groupIds().isEmpty() ? List.of(NO_GROUP) : query.groupIds());
        if (query.groupId() != null) params.put("groupId", query.groupId());
    }

    public SearchSql where(String condition) {
        conditions.add(condition);
        return this;
    }

    public SearchSql param(String name, Object value) {
        params.put(name, value);
        return this;
    }

    /** Na busca do grupo, só o do grupo; fora dela, o da pessoa e o dos grupos dela. */
    public SearchSql owned(String userColumn, String groupColumn) {
        return where(query.groupId() != null ? groupColumn + " = :groupId"
                : "(" + userColumn + " = :userId or " + groupColumn + " in (:groups))");
    }

    /** Só de grupos (do filtro ou todos da pessoa). */
    public SearchSql inGroups(String groupColumn) {
        return where(query.groupId() != null ? groupColumn + " = :groupId" : groupColumn + " in (:groups)");
    }

    /**
     * @param title título (bônus quando começa com o texto)
     * @param fuzzy campos curtos, que aceitam erro de digitação
     * @param exact campos longos, só com a palavra contida
     */
    public SearchSql text(String title, List<String> fuzzy, List<String> exact) {
        if (!query.hasText()) return this;
        List<String> all = new ArrayList<>(fuzzy);
        all.addAll(exact);
        String fuzzyDoc = "search_norm(concat_ws(' ', " + String.join(", ", fuzzy) + "))";
        String fullDoc = "search_norm(concat_ws(' ', " + String.join(", ", all) + "))";
        List<String> words = query.words();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            params.put("w" + i, SearchText.like(word));
            String condition = fullDoc + " like :w" + i + " escape '\\'";
            if (word.length() >= FUZZY_FROM) {
                params.put("f" + i, word);
                condition += " or strict_word_similarity(:f" + i + ", " + fuzzyDoc + ") >= " + SIMILAR;
            }
            conditions.add("(" + condition + ")");
        }
        params.put("q", query.text());
        params.put("prefix", SearchText.like(query.text()).substring(1));
        params.put("inside", SearchText.like(query.text()));
        String normTitle = "search_norm(coalesce(" + title + ", ''))";
        score = "(word_similarity(:q, " + fuzzyDoc + ") + case when " + normTitle + " like :prefix escape '\\' then 1 "
                + "when " + normTitle + " like :inside escape '\\' then 0.5 else 0 end)";
        return this;
    }

    public SearchSql dates(String column) {
        if (query.from() != null) {
            params.put("from", query.from());
            conditions.add(column + " >= :from");
        }
        if (query.to() != null) {
            params.put("to", query.to());
            conditions.add(column + " <= :to");
        }
        return this;
    }

    public SearchSql amounts(String column) {
        if (query.min() != null) {
            params.put("min", query.min());
            conditions.add(column + " >= :min");
        }
        if (query.max() != null) {
            params.put("max", query.max());
            conditions.add(column + " <= :max");
        }
        return this;
    }

    public SearchSql finance(String typeColumn, String categoryColumn) {
        if (query.type() != null) {
            params.put("type", query.type().name());
            conditions.add(typeColumn + " = :type");
        }
        if (query.categoryId() != null) {
            params.put("categoryId", query.categoryId());
            conditions.add(categoryColumn + " = :categoryId");
        }
        return this;
    }

    /**
     * @param select colunas (sem {@code score})
     * @param recent desempate depois da relevância (mais novo primeiro)
     */
    public <T> List<T> list(JdbcClient jdbc, String select, String from, String recent, RowMapper<T> mapper) {
        return page(jdbc, select, from, "score desc, " + recent + " desc nulls last", query.limit() + 1, 0, mapper);
    }

    /** @param order ordem completa (pode usar {@code score}) */
    public <T> List<T> page(JdbcClient jdbc, String select, String from, String order, int limit, int offset,
                            RowMapper<T> mapper) {
        String sql = "select " + select + ", " + score + " as score from " + from + where()
                + " order by " + order + " limit " + limit + " offset " + offset;
        return jdbc.sql(sql).params(params).query(mapper).list();
    }

    /** Uma linha agregada (contagem, somas) sobre tudo o que a busca acha. */
    public <T> T aggregate(JdbcClient jdbc, String select, String from, RowMapper<T> mapper) {
        return jdbc.sql("select " + select + " from " + from + where()).params(params).query(mapper).single();
    }

    public static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    public static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    public static BigDecimal money(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private String where() {
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }
}
