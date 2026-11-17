package com.lifepedia.api.vehicle;

import com.lifepedia.api.finance.EntryType;
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
import java.util.UUID;
import java.util.stream.Stream;

/** Veículos da pessoa e os compartilhados com ela (ou com os grupos dela), com gastos e manutenções, na busca geral. */
@Component
public class VehicleSearch implements SearchProvider {

    private static final String VISIBLE = "(v.user_id = :userId or exists (select 1 from vehicle_shares s "
            + "where s.vehicle_id = v.id and (s.user_id = :userId or s.group_id in (:groups))))";
    private static final Map<String, String> EXPENSE = Map.of(
            "FUEL", "Abastecimento", "SERVICE", "Oficina", "PART", "Peças", "TAX", "IPVA", "LICENSING", "Licenciamento",
            "INSURANCE", "Seguro", "FINE", "Multa", "TOLL", "Pedágio", "WASH", "Lavagem", "OTHER", "Outro gasto");

    private final JdbcClient jdbc;

    public VehicleSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.VEHICLE, SearchKind.VEHICLE_EXPENSE, SearchKind.VEHICLE_SERVICE);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        SearchSql sql = new SearchSql(query).where(VISIBLE);
        return switch (kind) {
            case VEHICLE -> sql.text("v.name", List.of("v.name", "v.brand", "v.model"), List.of("v.plate", "v.notes"))
                    .list(jdbc, "v.id, v.name, v.brand, v.model, v.model_year, v.plate", "vehicles v", "v.updated_at",
                            this::vehicle);
            case VEHICLE_EXPENSE -> sql
                    .text("e.description", List.of("e.description", "e.station", "v.name"), List.of("e.notes", "e.kind"))
                    .dates("e.spent_on").amounts("e.amount")
                    .list(jdbc, "e.id, e.vehicle_id, e.kind, e.description, e.station, e.amount, e.spent_on, v.name as vehicle",
                            "vehicle_expenses e join vehicles v on v.id = e.vehicle_id", "e.spent_on", this::expense);
            case VEHICLE_SERVICE -> sql.text("l.name", List.of("l.name", "v.name"), List.of("l.notes")).dates("l.done_on")
                    .list(jdbc, "l.id, l.vehicle_id, l.name, l.done_on, l.km, v.name as vehicle",
                            "vehicle_service_logs l join vehicles v on v.id = l.vehicle_id", "l.done_on", this::service);
            default -> List.of();
        };
    }

    private SearchHit vehicle(ResultSet rs, int row) throws SQLException {
        String model = String.join(" ", Stream.of(rs.getString("brand"), rs.getString("model"),
                rs.getString("model_year")).filter(s -> s != null && !s.isBlank()).toList());
        String plate = rs.getString("plate");
        return SearchHit.of(SearchKind.VEHICLE, SearchSql.uuid(rs, "id"), rs.getString("name"),
                        "/veiculos/" + SearchSql.uuid(rs, "id"), rs.getDouble("score"))
                .subtitle(plate == null ? model : model.isEmpty() ? plate : model + " · " + plate)
                .field("Modelo", model).field("Placa", plate)
                .build();
    }

    private SearchHit expense(ResultSet rs, int row) throws SQLException {
        UUID vehicleId = SearchSql.uuid(rs, "vehicle_id");
        String kind = EXPENSE.getOrDefault(rs.getString("kind"), "Gasto");
        String description = rs.getString("description");
        LocalDate day = SearchSql.date(rs, "spent_on");
        return SearchHit.of(SearchKind.VEHICLE_EXPENSE, SearchSql.uuid(rs, "id"),
                        description == null || description.isBlank() ? kind : description,
                        "/veiculos/" + vehicleId + "?aba=gastos", rs.getDouble("score"))
                .subtitle(rs.getString("vehicle") + " · " + kind + " · " + SearchHit.day(day)).date(day)
                .amount(rs.getBigDecimal("amount"), EntryType.EXPENSE)
                .field("Veículo", rs.getString("vehicle")).field("Tipo", kind).field("Data", SearchHit.day(day))
                .field("Posto", rs.getString("station"))
                .build();
    }

    private SearchHit service(ResultSet rs, int row) throws SQLException {
        LocalDate day = SearchSql.date(rs, "done_on");
        Integer km = rs.getObject("km", Integer.class);
        return SearchHit.of(SearchKind.VEHICLE_SERVICE, SearchSql.uuid(rs, "id"), rs.getString("name"),
                        "/veiculos/" + SearchSql.uuid(rs, "vehicle_id") + "?aba=manutencao", rs.getDouble("score"))
                .subtitle(rs.getString("vehicle") + " · " + SearchHit.day(day)).date(day)
                .field("Veículo", rs.getString("vehicle")).field("Feita em", SearchHit.day(day))
                .field("Km", km == null ? null : km + " km")
                .build();
    }
}
