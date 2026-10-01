package com.lifepedia.api.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "Informe seu nome.") @Size(max = 120) String name,
            @NotBlank(message = "Informe um nome de usuário.") @Size(max = 31) String username) {
    }

    /** Dados públicos de outro usuário (busca, membros). */
    public record UserSummary(UUID id, String name, String username) {

        public static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getName(), user.getUsername());
        }
    }
}
