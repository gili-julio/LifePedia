package com.lifepedia.api.group;

import com.lifepedia.api.user.UserDtos.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class GroupDtos {

    private GroupDtos() {
    }

    public record GroupRequest(@NotBlank(message = "Informe o nome do grupo.") @Size(max = 60) String name) {
    }

    /** @param imageUrl nulo sem imagem; @param role papel do usuário atual */
    public record GroupSummary(UUID id, String name, String imageUrl, long memberCount, GroupMember.Role role) {

        static GroupSummary of(UserGroup group, long memberCount, GroupMember.Role role) {
            return new GroupSummary(group.getId(), group.getName(), group.imageUrl(), memberCount, role);
        }
    }

    public record MemberResponse(UserSummary user, GroupMember.Role role, OffsetDateTime joinedAt) {
    }

    public record GroupDetail(UUID id, String name, String imageUrl, GroupMember.Role role, List<MemberResponse> members) {
    }

    public record RoleRequest(@NotNull(message = "Informe o papel.") GroupMember.Role role) {
    }

    public record InviteRequest(
            @NotEmpty(message = "Escolha quem convidar.")
            @Size(max = 20, message = "Convide até 20 pessoas por vez.") List<@NotNull UUID> userIds) {
    }

    public record InviteResult(int invited, int skipped) {
    }

    /** @param status MEMBER, INVITED ou nulo (pode convidar) */
    public record InviteCandidate(UUID id, String name, String username, String status) {
    }

    public record SentInvite(UUID id, UserSummary user, UserSummary invitedBy, OffsetDateTime createdAt) {
    }

    public record ReceivedInvite(UUID id, GroupSummary group, UserSummary invitedBy, OffsetDateTime createdAt) {
    }

    public record InviteLinkResponse(String token, OffsetDateTime expiresAt) {
    }

    /** Prévia do link de convite; {@code imageUrl} funciona com o token. */
    public record LinkPreview(UUID groupId, String name, String imageUrl, long memberCount, boolean member) {
    }

    public record CountResponse(long count) {
    }
}
