package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("lifepedia.jwt")
public record JwtProperties(String secret, String issuer, Duration expiration) {

    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("lifepedia.jwt.secret precisa ter pelo menos 32 caracteres (HS256).");
        }
    }
}
