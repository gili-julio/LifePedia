package com.lifepedia.api.group;

import com.lifepedia.api.achievement.Achievement;
import com.lifepedia.api.achievement.AchievementProgress;
import com.lifepedia.api.group.GroupDtos.GroupSummary;
import com.lifepedia.api.group.GroupDtos.InviteCandidate;
import com.lifepedia.api.group.GroupDtos.InviteLinkResponse;
import com.lifepedia.api.group.GroupDtos.InviteRequest;
import com.lifepedia.api.group.GroupDtos.InviteResult;
import com.lifepedia.api.group.GroupDtos.LinkPreview;
import com.lifepedia.api.group.GroupDtos.ReceivedInvite;
import com.lifepedia.api.group.GroupDtos.SentInvite;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.image.ImageFile;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import com.lifepedia.api.user.Usernames;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Convites diretos (aceitar/recusar) e link de convite. Convidar e gerenciar o link: só admins. */
@Service
public class GroupInviteService {

    static final Duration LINK_VALIDITY = Duration.ofDays(7);
    private static final int MIN_SEARCH = 2;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GroupInviteRepository invites;
    private final GroupInviteLinkRepository links;
    private final GroupMemberRepository members;
    private final UserGroupRepository groups;
    private final UserRepository users;
    private final GroupAccess access;
    private final GroupService groupService;
    private final NotificationService notifications;
    private final GroupActivity activity;
    private final ApplicationEventPublisher events;

    public GroupInviteService(GroupInviteRepository invites, GroupInviteLinkRepository links,
                              GroupMemberRepository members, UserGroupRepository groups, UserRepository users,
                              GroupAccess access, GroupService groupService, NotificationService notifications,
                              GroupActivity activity, ApplicationEventPublisher events) {
        this.invites = invites;
        this.links = links;
        this.members = members;
        this.groups = groups;
        this.users = users;
        this.access = access;
        this.groupService = groupService;
        this.notifications = notifications;
        this.activity = activity;
        this.events = events;
    }

    /** Busca por nome ou @username (mín. 2 caracteres, até 10), marcando quem já é membro ou convidado. */
    @Transactional(readOnly = true)
    public List<InviteCandidate> candidates(UUID userId, UUID groupId, String query) {
        access.requireAdmin(groupId, userId);
        String term = Usernames.normalize(query);
        if (term.length() < MIN_SEARCH) return List.of();
        Set<UUID> memberIds = members.findByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .map(GroupMember::getUserId).collect(Collectors.toSet());
        Set<UUID> invitedIds = invites.findByGroupIdAndStatusOrderByCreatedAtDesc(groupId, GroupInvite.Status.PENDING)
                .stream().map(GroupInvite::getUserId).collect(Collectors.toSet());
        return users.search(term, userId, PageRequest.of(0, 10)).stream()
                .map(u -> new InviteCandidate(u.getId(), u.getName(), u.getUsername(), u.imageUrl(),
                        memberIds.contains(u.getId()) ? "MEMBER" : invitedIds.contains(u.getId()) ? "INVITED" : null))
                .toList();
    }

    /** Cria convites pendentes e notifica; ignora quem já é membro, já foi convidado ou não tem username. */
    @Transactional
    public InviteResult invite(UUID userId, UUID groupId, InviteRequest request) {
        access.requireAdmin(groupId, userId);
        UserGroup group = access.group(groupId);
        User inviter = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        Set<UUID> ids = new LinkedHashSet<>(request.userIds());
        int invited = 0;
        for (UUID id : ids) {
            User target = users.findById(id).orElse(null);
            boolean skip = target == null || target.getUsername() == null || id.equals(userId)
                    || members.existsByGroupIdAndUserId(groupId, id)
                    || !invites.findByGroupIdAndUserIdAndStatus(groupId, id, GroupInvite.Status.PENDING).isEmpty();
            if (skip) continue;
            GroupInvite invite = invites.save(new GroupInvite(groupId, id, userId));
            notifications.notify(id, NotificationType.GROUP_INVITE, userId, groupId, Map.of(
                    "actorName", inviter.getName(),
                    "actorUsername", inviter.getUsername() == null ? "" : inviter.getUsername(),
                    "groupName", group.getName(),
                    "inviteId", invite.getId().toString()));
            invited++;
        }
        return new InviteResult(invited, ids.size() - invited);
    }

    @Transactional(readOnly = true)
    public List<SentInvite> sent(UUID userId, UUID groupId) {
        access.requireAdmin(groupId, userId);
        List<GroupInvite> pending = invites.findByGroupIdAndStatusOrderByCreatedAtDesc(groupId, GroupInvite.Status.PENDING);
        Map<UUID, User> byId = usersOf(pending.stream().flatMap(i -> Stream.of(i.getUserId(), i.getInvitedBy())).toList());
        return pending.stream()
                .map(i -> new SentInvite(i.getId(), UserSummary.from(byId.get(i.getUserId())),
                        UserSummary.from(byId.get(i.getInvitedBy())), i.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void cancel(UUID userId, UUID groupId, UUID inviteId) {
        access.requireAdmin(groupId, userId);
        pending(invites.findByIdAndGroupId(inviteId, groupId).orElse(null)).setStatus(GroupInvite.Status.CANCELED);
    }

    /** Convites pendentes recebidos ("Solicitações"). */
    @Transactional(readOnly = true)
    public List<ReceivedInvite> received(UUID userId) {
        List<GroupInvite> pending = invites.findByUserIdAndStatusOrderByCreatedAtDesc(userId, GroupInvite.Status.PENDING);
        if (pending.isEmpty()) return List.of();
        List<UUID> groupIds = pending.stream().map(GroupInvite::getGroupId).distinct().toList();
        Map<UUID, UserGroup> groupById = groups.findAllById(groupIds).stream()
                .collect(Collectors.toMap(UserGroup::getId, Function.identity()));
        Map<UUID, Long> counts = groupService.memberCounts(groupIds);
        Map<UUID, User> inviters = usersOf(pending.stream().map(GroupInvite::getInvitedBy).toList());
        return pending.stream()
                .map(i -> new ReceivedInvite(i.getId(),
                        GroupSummary.of(groupById.get(i.getGroupId()), counts.getOrDefault(i.getGroupId(), 0L), null),
                        UserSummary.from(inviters.get(i.getInvitedBy())), i.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public long receivedCount(UUID userId) {
        return invites.countByUserIdAndStatus(userId, GroupInvite.Status.PENDING);
    }

    @Transactional
    public GroupSummary accept(UUID userId, UUID inviteId) {
        GroupInvite invite = pending(invites.findByIdAndUserId(inviteId, userId).orElse(null));
        invite.setStatus(GroupInvite.Status.ACCEPTED);
        return join(userId, access.group(invite.getGroupId()));
    }

    @Transactional
    public void decline(UUID userId, UUID inviteId) {
        pending(invites.findByIdAndUserId(inviteId, userId).orElse(null)).setStatus(GroupInvite.Status.DECLINED);
    }

    @Transactional(readOnly = true)
    public InviteLinkResponse link(UUID userId, UUID groupId) {
        access.requireAdmin(groupId, userId);
        return links.findByGroupId(groupId)
                .filter(l -> !l.isExpired())
                .map(l -> new InviteLinkResponse(l.getToken(), l.getExpiresAt()))
                .orElseThrow(() -> new NotFoundException("Nenhum link de convite ativo."));
    }

    /** Gera um link novo (o anterior deixa de funcionar), válido por 7 dias. */
    @Transactional
    public InviteLinkResponse renewLink(UUID userId, UUID groupId) {
        access.requireAdmin(groupId, userId);
        GroupInviteLink link = links.findByGroupId(groupId).orElseGet(() -> new GroupInviteLink(groupId));
        link.renew(newToken(), OffsetDateTime.now().plus(LINK_VALIDITY), userId);
        links.save(link);
        return new InviteLinkResponse(link.getToken(), link.getExpiresAt());
    }

    @Transactional
    public void revokeLink(UUID userId, UUID groupId) {
        access.requireAdmin(groupId, userId);
        links.findByGroupId(groupId).ifPresent(links::delete);
    }

    @Transactional(readOnly = true)
    public LinkPreview preview(UUID userId, String token) {
        UserGroup group = access.group(validLink(token).getGroupId());
        return new LinkPreview(group.getId(), group.getName(),
                group.getImageKey() == null ? null : "/api/groups/join/" + token + "/image?v=" + group.getImageKey(),
                members.countByGroupId(group.getId()), members.existsByGroupIdAndUserId(group.getId(), userId));
    }

    @Transactional(readOnly = true)
    public ImageFile linkImage(String token) {
        return groupService.imageOf(access.group(validLink(token).getGroupId()));
    }

    /** Entra pelo link; convites pendentes do mesmo grupo viram aceitos. */
    @Transactional
    public GroupSummary joinByLink(UUID userId, String token) {
        UserGroup group = access.group(validLink(token).getGroupId());
        invites.findByGroupIdAndUserIdAndStatus(group.getId(), userId, GroupInvite.Status.PENDING)
                .forEach(i -> i.setStatus(GroupInvite.Status.ACCEPTED));
        return join(userId, group);
    }

    private GroupSummary join(UUID userId, UserGroup group) {
        GroupMember member = members.findByGroupIdAndUserId(group.getId(), userId)
                .orElseGet(() -> members.save(new GroupMember(group.getId(), userId, GroupMember.Role.MEMBER)));
        activity.touch(group.getId(), OffsetDateTime.now());
        events.publishEvent(new AchievementProgress(userId, EnumSet.of(Achievement.GROUPS)));
        return GroupSummary.of(group, members.countByGroupId(group.getId()), member);
    }

    private GroupInviteLink validLink(String token) {
        return links.findByToken(token == null ? "" : token)
                .filter(l -> !l.isExpired())
                .orElseThrow(() -> new NotFoundException("Convite inválido ou expirado."));
    }

    private static GroupInvite pending(GroupInvite invite) {
        if (invite == null || invite.getStatus() != GroupInvite.Status.PENDING) {
            throw new NotFoundException("Convite não encontrado ou já respondido.");
        }
        return invite;
    }

    private Map<UUID, User> usersOf(List<UUID> ids) {
        return users.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 32 bytes aleatórios em base64url (43 caracteres). */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
