package com.lifepedia.api.notification.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PushDtos {

    private PushDtos() {
    }

    /** @param publicKey chave VAPID para o {@code pushManager.subscribe}; nula = push desligado no servidor */
    public record PushConfig(String publicKey) {
    }

    /**
     * Inscrição do navegador ({@code PushSubscription.toJSON()}).
     *
     * @param auto sincronização ao abrir o app: só religa um aparelho que já era do usuário, nunca cria
     */
    public record SubscriptionRequest(
            @NotBlank(message = "Informe o endpoint.") @Size(max = 1000, message = "Endpoint muito longo.") String endpoint,
            @NotNull(message = "Informe as chaves.") @Valid Keys keys,
            Boolean auto) {
    }

    public record Keys(
            @NotBlank(message = "Informe a chave p256dh.") @Size(max = 100) String p256dh,
            @NotBlank(message = "Informe a chave auth.") @Size(max = 50) String auth) {
    }

    /** @param active este aparelho recebe push desta conta */
    public record SubscriptionResponse(boolean active) {
    }

    public record UnsubscribeRequest(@NotBlank(message = "Informe o endpoint.") @Size(max = 1000) String endpoint) {
    }
}
