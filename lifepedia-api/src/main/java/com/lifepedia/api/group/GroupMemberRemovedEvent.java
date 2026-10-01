package com.lifepedia.api.group;

import java.util.UUID;

/** Publicado (síncrono, mesma transação) quando alguém sai ou é removido; módulos limpam os dados dele no grupo. */
public record GroupMemberRemovedEvent(UUID groupId, UUID userId) {
}
