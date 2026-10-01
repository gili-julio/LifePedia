package com.lifepedia.api.user;

import java.util.UUID;

/** Publicado no cadastro (síncrono, mesma transação) para módulos criarem dados iniciais. */
public record UserRegisteredEvent(UUID userId) {
}
