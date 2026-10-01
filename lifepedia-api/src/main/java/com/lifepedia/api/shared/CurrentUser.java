package com.lifepedia.api.shared;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** Id do usuário a partir do {@code sub} do JWT. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
