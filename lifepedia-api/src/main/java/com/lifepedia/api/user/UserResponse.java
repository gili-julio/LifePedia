package com.lifepedia.api.user;

import java.time.OffsetDateTime;
import java.util.UUID;

/** @param profileComplete false = falta dado obrigatório (ex.: username); o front leva ao perfil */
public record UserResponse(UUID id, String name, String username, String email, OffsetDateTime createdAt,
                           boolean profileComplete) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getUsername(), user.getEmail(), user.getCreatedAt(),
                user.isProfileComplete());
    }
}
