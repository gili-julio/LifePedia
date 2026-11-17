package com.lifepedia.api.vehicle;

import java.util.Locale;

/** Tabelas da FIPE; {@code path} = caminho na API. */
public enum FipeKind {
    CARS, MOTORCYCLES, TRUCKS;

    public String path() {
        return name().toLowerCase(Locale.ROOT);
    }
}
