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

    public ResponseCookie session(String token, Duration maxAge) {
        return ResponseCookie.from(properties.cookieName(), token)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/api")
                .maxAge(maxAge)
                .build();
    }

    /** Apaga o cookie (logout, conta excluída). */
    public ResponseCookie expired() {
        return session("", Duration.ZERO);
    }
}
