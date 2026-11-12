package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Rastreio de entregas.
 *
 * @param apiKey chave da API do 17TRACK (cabeçalho {@code 17token}); vazia = guarda o código sem rastrear
 */
@ConfigurationProperties("lifepedia.deliveries")
public record DeliveryProperties(String apiKey) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Configuration
    @EnableConfigurationProperties(DeliveryProperties.class)
    static class Config {
    }
}
