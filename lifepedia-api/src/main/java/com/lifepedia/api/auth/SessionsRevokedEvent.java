package com.lifepedia.api.auth;

import java.util.UUID;

/** Todas as sessões da pessoa foram encerradas (bloqueio ou "encerrar sessões" no painel). */
public record SessionsRevokedEvent(UUID userId) {
}
