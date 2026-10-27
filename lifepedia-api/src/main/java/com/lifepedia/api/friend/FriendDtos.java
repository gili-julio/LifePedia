package com.lifepedia.api.friend;

import com.lifepedia.api.achievement.AchievementDtos.AchievementResponse;
import com.lifepedia.api.user.UserDtos.UserSummary;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class FriendDtos {

    private FriendDtos() {
    }

    public record FriendRequest(@NotNull(message = "Escolha quem adicionar.") UUID userId) {
    }

    /** @param status {@code PENDING} (pedido enviado) ou {@code ACCEPTED} (a pessoa já tinha pedido: viraram amigos) */
    public record FriendRequestResult(Friendship.Status status) {
    }

    public record FriendResponse(UserSummary user, OffsetDateTime since) {
    }

    public record RequestResponse(UUID id, UserSummary user, OffsetDateTime createdAt) {
    }

    public record Requests(List<RequestResponse> received, List<RequestResponse> sent) {
    }

    /** @param status FRIEND, SENT (pedido meu), RECEIVED (pedido dela) ou nulo (pode adicionar) */
    public record Candidate(UUID id, String name, String username, String imageUrl, String status) {
    }

    public record FriendProfile(UserSummary user, OffsetDateTime since, List<AchievementResponse> achievements) {
    }

    public record CountResponse(long count) {
    }
}
