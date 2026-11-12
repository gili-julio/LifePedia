package com.lifepedia.api.integration;

import java.util.UUID;

/** Conta externa pronta para chamar o serviço (token já aberto, só na memória e nunca em log). */
public record Connection(UUID accountId, UUID userId, IntegrationProvider provider, String nickname, String baseUrl,
                         String externalId, String login, String token, boolean canWrite) {

    @Override
    public String toString() {
        return "Connection[" + provider + " " + accountId + "]";
    }
}
