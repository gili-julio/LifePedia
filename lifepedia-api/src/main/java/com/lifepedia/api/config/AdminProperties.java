package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/** @param email conta que é sempre admin (a primeira); vazio = nenhuma fixa */
@ConfigurationProperties("lifepedia.admin")
public record AdminProperties(String email) {

    /** E-mail em minúsculas, como gravado nas contas; nulo se não configurado. */
    public String normalizedEmail() {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isFixed(String userEmail) {
        return userEmail != null && userEmail.equals(normalizedEmail());
    }

    @Configuration
    @EnableConfigurationProperties(AdminProperties.class)
    static class Config {
    }
}
