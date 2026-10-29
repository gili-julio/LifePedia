package com.lifepedia.api.auth;

import com.lifepedia.api.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Cookie HttpOnly da sessão no navegador. */
@Component
public class SessionCookies {

    private final AuthProperties properties;

    public SessionCookies(AuthProperties properties) {
        this.properties = properties;
    }

    /** @param maxAge nulo = cookie de sessão do navegador (some ao fechar), sem "lembrar de mim" */
    public ResponseCookie session(String token, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(properties.cookieName(), token)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/api");
        if (maxAge != null) cookie.maxAge(maxAge);
        return cookie.build();
    }

    /** Apaga o cookie (logout, conta excluída). */
    public ResponseCookie expired() {
        return session("", Duration.ZERO);
    }
}
