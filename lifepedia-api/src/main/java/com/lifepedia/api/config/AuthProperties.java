package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param cookieName      cookie de sessão
 * @param cookieSecure    cookie só em HTTPS
 * @param ipLimit         login/cadastro por IP na janela
 * @param ipWindow        janela do limite por IP
 * @param maxFailedLogins senhas erradas antes do bloqueio
 * @param lockoutWindow   duração do bloqueio por e-mail
 * @param rememberFor     validade da sessão com "lembrar de mim"
 */
@ConfigurationProperties("lifepedia.auth")
public record AuthProperties(
        String cookieName,
        boolean cookieSecure,
        int ipLimit,
        Duration ipWindow,
        int maxFailedLogins,
        Duration lockoutWindow,
        Duration rememberFor) {
}
