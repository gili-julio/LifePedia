package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param clientId OAuth Client ID do Google; vazio = login com Google desligado */
@ConfigurationProperties("lifepedia.google")
public record GoogleProperties(String clientId) {

    public boolean enabled() {
        return clientId != null && !clientId.isBlank();
    }
}
