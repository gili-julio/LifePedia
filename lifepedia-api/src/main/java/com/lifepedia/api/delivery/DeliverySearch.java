package com.lifepedia.api.delivery;

import com.lifepedia.api.search.SearchHit;
import com.lifepedia.api.search.SearchKind;
import com.lifepedia.api.search.SearchProvider;
import com.lifepedia.api.search.SearchQuery;
import com.lifepedia.api.search.SearchSql;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Entregas da pessoa e as compartilhadas com os grupos dela na busca geral. */
@Component
public class DeliverySearch implements SearchProvider {

    private static final Map<String, String> STATUS = Map.of(
            "PENDING", "Sem informação", "POSTED", "Postado", "IN_TRANSIT", "Em trânsito", "PICKUP", "Para retirar",
            "OUT_FOR_DELIVERY", "Saiu para entrega", "DELIVERED", "Entregue", "FAILED", "Falha na entrega",
            "EXCEPTION", "Problema", "EXPIRED", "Sem atualização");

    private final JdbcClient jdbc;

    public DeliverySearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.DELIVERY);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        return new SearchSql(query).owned("d.user_id", "d.group_id")
                .text("d.name", List.of("d.name", "d.carrier_name"), List.of("d.tracking_code", "d.latest_text"))
                .dates("d.created_at::date")
                .list(jdbc, "d.id, d.name, d.tracking_code, d.status, d.carrier_name, d.latest_text, d.estimated_to, "
                                + "d.created_at::date as day, d.archived_at is not null as archived, g.name as group_name",
                        "deliveries d left join user_groups g on g.id = d.group_id", "d.created_at", this::hit);
    }

    private SearchHit hit(ResultSet rs, int row) throws SQLException {
        String status = STATUS.getOrDefault(rs.getString("status"), "");
        String code = rs.getString("tracking_code");
        LocalDate estimated = SearchSql.date(rs, "estimated_to");
        return SearchHit.of(SearchKind.DELIVERY, SearchSql.uuid(rs, "id"), rs.getString("name"),
                        "/entregas/" + SearchSql.uuid(rs, "id"), rs.getDouble("score"))
                .subtitle(status + " · " + code + (rs.getBoolean("archived") ? " · histórico" : ""))
                .group(rs.getString("group_name")).date(SearchSql.date(rs, "day"))
                .field("Situação", status).field("Código", code).field("Transportadora", rs.getString("carrier_name"))
                .field("Última atualização", rs.getString("latest_text")).field("Previsão", SearchHit.day(estimated))
                .build();
    }
}
