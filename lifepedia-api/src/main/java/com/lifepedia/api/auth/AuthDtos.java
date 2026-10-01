package com.lifepedia.api.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifepedia.api.user.UserResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Informe seu nome.") @Size(max = 120) String name,
            @NotBlank(message = "Informe um nome de usuário.") @Size(max = 31) String username,
            @NotBlank(message = "Informe seu e-mail.") @Email(message = "E-mail inválido.") @Size(max = 180) String email,
            @NotBlank(message = "Informe uma senha.") @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Informe seu e-mail.") @Size(max = 180) String email,
            @NotBlank(message = "Informe sua senha.") @Size(max = 200) String password) {
    }

    /** @param token só com {@code X-Auth-Mode: token}; no navegador fica no cookie */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthResponse(String token, Instant expiresAt, UserResponse user) {

        AuthResponse withoutToken() {
            return new AuthResponse(null, expiresAt, user);
        }
    }
}
