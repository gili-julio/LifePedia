package com.lifepedia.api.vehicle;

/** Tipo do veículo; {@code fipe} = tabela da FIPE que vale para ele (nulo = sem FIPE). */
public enum VehicleType {
    CAR(FipeKind.CARS),
    MOTORCYCLE(FipeKind.MOTORCYCLES),
    TRUCK(FipeKind.TRUCKS),
    BICYCLE(null),
    OTHER(null);

    private final FipeKind fipe;

    VehicleType(FipeKind fipe) {
        this.fipe = fipe;
    }

    public FipeKind fipe() {
        return fipe;
    }
}
