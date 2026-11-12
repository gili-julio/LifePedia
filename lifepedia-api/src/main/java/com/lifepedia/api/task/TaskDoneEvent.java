package com.lifepedia.api.task;

import java.util.UUID;

/** Tarefa sem repetição concluída (publicado na transação; outros módulos reagem depois do commit). */
public record TaskDoneEvent(UUID taskId, UUID userId) {
}
