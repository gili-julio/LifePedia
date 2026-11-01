package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Gemini da importação pelo site; chave vazia = desligada.
 *
 * @param timeout    tempo máximo de uma leitura (número puro = segundos)
 * @param dailyLimit leituras por dia somando todas as contas, para proteger a cota e o custo da chave
 */
@ConfigurationProperties("lifepedia.ai")
public record AiProperties(String apiKey, String model, @DurationUnit(ChronoUnit.SECONDS) Duration timeout,
                           int dailyLimit, String baseUrl) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Configuration
    @EnableConfigurationProperties(AiProperties.class)
    static class Config {
    }
}
