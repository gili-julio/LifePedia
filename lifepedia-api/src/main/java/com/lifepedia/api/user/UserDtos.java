package com.lifepedia.api.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "Informe seu nome.") @Size(max = 120) String name,
            @NotBlank(message = "Informe um nome de usuário.") @Size(max = 31) String username) {
    }

    /** Dados públicos de outro usuário (busca, membros). @param imageUrl nulo sem foto */
    /** @param version versão dos Termos/Política que a pessoa leu (tem que ser a vigente) */
    public record TermsRequest(@NotBlank(message = "Informe a versão.") @Size(max = 20) String version) {
    }

    /** @param hiddenModules módulos que somem do menu e do Início */
    public record PreferencesRequest(
            @NotNull(message = "Escolha o formato do menu.") Boolean sidebarIcons,
            @NotNull @Size(max = 20) Set<@NotNull AppModule> hiddenModules) {
    }

    public record UserSummary(UUID id, String name, String username, String imageUrl) {

        public static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getName(), user.getUsername(), user.imageUrl());
        }
    }
}
