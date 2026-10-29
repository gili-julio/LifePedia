package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Chaves VAPID (base64url) do Web Push; vazias = push desligado.
 *
 * @param subject      contato exigido pelos serviços de push ({@code mailto:} ou {@code https:})
 * @param allowedHosts serviços de push aceitos no endpoint (o host ou um subdomínio dele)
 */
@ConfigurationProperties("lifepedia.push")
public record PushProperties(String publicKey, String privateKey, String subject, List<String> allowedHosts) {

    public boolean enabled() {
        return filled(publicKey) && filled(privateKey) && filled(subject);
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }

    @Configuration
    @EnableConfigurationProperties(PushProperties.class)
    static class Config {
    }
}
