package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Veículos.
 *
 * @param fipeToken token da API da FIPE (parallelum, cabeçalho {@code X-Subscription-Token}); vazio = cota sem token
 */
@ConfigurationProperties("lifepedia.vehicles")
public record VehicleProperties(String fipeToken) {

    public boolean hasToken() {
        return fipeToken != null && !fipeToken.isBlank();
    }

    @Configuration
    @EnableConfigurationProperties(VehicleProperties.class)
    static class Config {
    }
}
