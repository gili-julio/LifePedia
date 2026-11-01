package com.lifepedia.api.financing;

import java.util.List;

/** Tipo do bem: define a modalidade de juros, os gastos padrão e os prazos comuns. */
public enum FinancingKind {
    HOUSE(Modality.HOUSING, 360, 420, List.of(120, 180, 240, 300, 360, 420)),
    APARTMENT(Modality.HOUSING, 360, 420, List.of(120, 180, 240, 300, 360, 420)),
    CAR(Modality.VEHICLE, 48, 72, List.of(12, 24, 36, 48, 60)),
    MOTORCYCLE(Modality.VEHICLE, 36, 60, List.of(12, 24, 36, 48)),
    OTHER(Modality.OTHER, 24, 60, List.of(6, 12, 24, 36, 48));

    private final Modality modality;
    private final int defaultMonths;
    private final int maxMonths;
    private final List<Integer> terms;

    FinancingKind(Modality modality, int defaultMonths, int maxMonths, List<Integer> terms) {
        this.modality = modality;
        this.defaultMonths = defaultMonths;
        this.maxMonths = maxMonths;
        this.terms = terms;
    }

    public Modality modality() {
        return modality;
    }

    public boolean isHousing() {
        return modality == Modality.HOUSING;
    }

    public boolean isVehicle() {
        return modality == Modality.VEHICLE;
    }

    public int defaultMonths() {
        return defaultMonths;
    }

    public int maxMonths() {
        return maxMonths;
    }

    /** Prazos mostrados lado a lado na simulação. */
    public List<Integer> terms() {
        return terms;
    }
}
