package com.lifepedia.api.vehicle;

import java.util.List;

/** Tabela sugerida de manutenção por tipo (intervalos comuns; o manual do veículo vale mais e tudo é editável). */
public final class VehicleTemplates {

    public record Template(String name, Integer intervalKm, Integer intervalMonths) {
    }

    private static final List<Template> CAR = List.of(
            new Template("Óleo do motor", 10_000, 12),
            new Template("Filtro de óleo", 10_000, 12),
            new Template("Filtro de ar do motor", 15_000, 12),
            new Template("Filtro de cabine (ar-condicionado)", 15_000, 12),
            new Template("Filtro de combustível", 20_000, 24),
            new Template("Alinhamento e balanceamento", 10_000, 12),
            new Template("Rodízio dos pneus", 10_000, null),
            new Template("Pastilhas de freio", 20_000, null),
            new Template("Fluido de freio", null, 24),
            new Template("Velas de ignição", 40_000, 48),
            new Template("Líquido de arrefecimento", 40_000, 24),
            new Template("Correia dentada", 60_000, 60),
            new Template("Bateria (conferir)", null, 24));

    private static final List<Template> MOTORCYCLE = List.of(
            new Template("Óleo do motor", 3_000, 6),
            new Template("Filtro de óleo", 6_000, 12),
            new Template("Lubrificar a corrente", 500, 1),
            new Template("Ajustar a folga da corrente", 1_000, null),
            new Template("Kit relação (corrente, coroa e pinhão)", 20_000, null),
            new Template("Filtro de ar", 6_000, 12),
            new Template("Vela de ignição", 10_000, 12),
            new Template("Pastilhas de freio", 10_000, null),
            new Template("Fluido de freio", null, 24),
            new Template("Pneus (conferir desgaste)", 10_000, 12),
            new Template("Regulagem de válvulas", 12_000, null));

    private static final List<Template> TRUCK = List.of(
            new Template("Óleo do motor", 15_000, 6),
            new Template("Filtro de óleo", 15_000, 6),
            new Template("Filtro de combustível", 15_000, 6),
            new Template("Filtro de ar", 30_000, 12),
            new Template("Óleo da caixa e do diferencial", 80_000, 24),
            new Template("Lonas/pastilhas de freio", 30_000, null),
            new Template("Alinhamento e balanceamento", 20_000, 12),
            new Template("Fluido de freio", null, 24));

    private static final List<Template> BICYCLE = List.of(
            new Template("Lubrificar a corrente", 200, 1),
            new Template("Revisão geral", 1_000, 6),
            new Template("Pastilhas/sapatas de freio", 1_500, null),
            new Template("Corrente (trocar)", 3_000, null),
            new Template("Pneus (conferir desgaste)", 3_000, 12));

    private VehicleTemplates() {
    }

    public static List<Template> of(VehicleType type) {
        return switch (type) {
            case CAR, OTHER -> CAR;
            case MOTORCYCLE -> MOTORCYCLE;
            case TRUCK -> TRUCK;
            case BICYCLE -> BICYCLE;
        };
    }
}
