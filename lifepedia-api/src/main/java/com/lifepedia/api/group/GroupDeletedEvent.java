package com.lifepedia.api.group;

import java.util.UUID;

/** Publicado (síncrono) antes de excluir o grupo; módulos limpam o que o banco não apaga sozinho (arquivos). */
public record GroupDeletedEvent(UUID groupId) {
}
