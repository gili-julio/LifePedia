package com.lifepedia.api.user;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @param imageUrl        nulo sem foto
 * @param profileComplete false = falta dado obrigatório (ex.: username); o front leva ao perfil
 */
public record UserResponse(UUID id, String name, String username, String email, String imageUrl,
                           OffsetDateTime createdAt, boolean profileComplete) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getUsername(), user.getEmail(), user.imageUrl(),
                user.getCreatedAt(), user.isProfileComplete());
    }
}
