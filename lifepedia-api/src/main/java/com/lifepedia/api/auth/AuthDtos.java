package com.lifepedia.api.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifepedia.api.user.UserResponse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private static final String TERMS = "Aceite os Termos de uso e a Política de privacidade.";

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Informe seu nome.") @Size(max = 120) String name,
            @NotBlank(message = "Informe um nome de usuário.") @Size(max = 31) String username,
            @NotBlank(message = "Informe seu e-mail.") @Email(message = "E-mail inválido.") @Size(max = 180) String email,
            @NotBlank(message = "Informe uma senha.")
            @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String password,
            @NotNull(message = TERMS) @AssertTrue(message = TERMS) Boolean acceptTerms) {
    }

    /** @param remember "Lembrar de mim" */
    public record LoginRequest(
            @NotBlank(message = "Informe seu e-mail.") @Size(max = 180) String email,
            @NotBlank(message = "Informe sua senha.") @Size(max = 200) String password,
            Boolean remember) {
    }

    /** @param credential ID token devolvido pelo botão do Google */
    public record GoogleLoginRequest(
            @NotBlank(message = "Credencial do Google ausente.") @Size(max = 4096) String credential,
            Boolean remember) {
    }

    public record GoogleLinkRequest(@NotBlank(message = "Credencial do Google ausente.") @Size(max = 4096) String credential) {
    }

    /** Configuração pública da tela de login. @param googleClientId nulo = sem botão do Google */
    public record AuthConfig(String googleClientId) {
    }

    /**
     * @param token    só com {@code X-Auth-Mode: token}; no navegador fica no cookie
     * @param remember cookie persistente ("lembrar de mim")
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthResponse(String token, Instant expiresAt, boolean remember, UserResponse user) {

        AuthResponse withoutToken() {
            return new AuthResponse(null, expiresAt, remember, user);
        }
    }
}
