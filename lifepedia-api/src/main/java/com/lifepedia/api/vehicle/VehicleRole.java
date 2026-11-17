package com.lifepedia.api.vehicle;

/**
 * Papel no veículo, do menor para o maior: ver, lançar (km, gastos, manutenção feita), editar (dados, itens, revisões,
 * documentos). {@code OWNER} só o dono (nunca gravado).
 */
public enum VehicleRole {
    VIEW, LOG, EDIT, OWNER;

    public boolean atLeast(VehicleRole other) {
        return compareTo(other) >= 0;
    }
}
