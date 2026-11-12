package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Contas externas (central de integrações e módulo Dev).
 *
 * @param key                chave AES de 32 bytes em base64 que cifra os tokens; vazia = só o Duolingo (sem token)
 * @param googleClientSecret segredo do mesmo OAuth Client do login com Google; vazio = Google Agenda desligado
 * @param appUrl             endereço público do site (volta do Google); vazio = primeira origem do CORS
 */
@ConfigurationProperties("lifepedia.integrations")
public record IntegrationProperties(String key, String googleClientSecret, String appUrl) {

    public boolean tokensEnabled() {
        return key != null && !key.isBlank();
    }

    @Configuration
    @EnableConfigurationProperties(IntegrationProperties.class)
    static class Config {
    }
}
