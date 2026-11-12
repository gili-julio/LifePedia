package com.lifepedia.api.integration;

import java.time.Duration;

/** Serviços externos que dá para ligar; {@code interval} = de quanto em quanto tempo sincroniza sozinho. */
public enum IntegrationProvider {
    GOOGLE(Duration.ofMinutes(15), true),
    GITLAB(Duration.ofMinutes(10), true),
    GITHUB(Duration.ofMinutes(10), true),
    DUOLINGO(Duration.ofMinutes(60), false);

    private final Duration interval;
    private final boolean token;

    IntegrationProvider(Duration interval, boolean token) {
        this.interval = interval;
        this.token = token;
    }

    public Duration interval() {
        return interval;
    }

    /** Precisa de token (e da chave de cifra no servidor). */
    public boolean usesToken() {
        return token;
    }

    public boolean isCodeHost() {
        return this == GITLAB || this == GITHUB;
    }
}
