package com.lifepedia.api.chat;

import com.lifepedia.api.user.UserDtos.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record MessageRequest(
            @NotBlank(message = "Escreva a mensagem.") @Size(max = 2000, message = "Máximo de 2000 caracteres.") String body,
            UUID replyToId) {
    }

    public record EditRequest(
            @NotBlank(message = "Escreva a mensagem.") @Size(max = 2000, message = "Máximo de 2000 caracteres.") String body) {
    }

    /** Trecho da mensagem respondida. @param body nulo se ela foi apagada */
    public record ReplyPreview(UUID id, UserSummary author, String body) {
    }

    /** @param body nulo se apagada; {@code replyTo} nulo sem resposta (ou se a original sumiu) */
    public record MessageResponse(UUID id, UUID groupId, UserSummary author, String body, ReplyPreview replyTo,
                                  OffsetDateTime createdAt, OffsetDateTime editedAt, boolean deleted) {
    }

    /** Página do histórico, da mais antiga para a mais nova. @param hasMore há mensagens antes da primeira */
    public record MessagePage(List<MessageResponse> items, boolean hasMore) {
    }

    /** Evento do stream: mensagem nova, editada ou apagada (o front substitui pelo id). */
    public record ChatEvent(List<UUID> recipients, MessageResponse message) {
    }
}
