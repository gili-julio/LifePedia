package com.lifepedia.api.architecture;

import com.lifepedia.api.architecture.ArchitectureDtos.GroupRef;
import com.lifepedia.api.architecture.ArchitectureDtos.GroupShare;
import com.lifepedia.api.architecture.ArchitectureDtos.LinkResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.PublicProject;
import com.lifepedia.api.architecture.ArchitectureDtos.PublicVersion;
import com.lifepedia.api.architecture.ArchitectureDtos.ShareOptions;
import com.lifepedia.api.architecture.ArchitectureDtos.Shares;
import com.lifepedia.api.architecture.ArchitectureDtos.UserShare;
import com.lifepedia.api.architecture.ArchitectureFiles.Type;
import com.lifepedia.api.friend.Friendship;
import com.lifepedia.api.friend.FriendshipRepository;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.group.UserGroup;
import com.lifepedia.api.group.UserGroupRepository;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compartilhamento (só o dono): com amigos e com grupos de que o dono participa, com papel, e links públicos com
 * validade. Quem recebe é avisado ({@code ARCHITECTURE_SHARED}).
 */
@Service
public class ArchShareService {

    static final int MAX_SHARES = 50;
    static final int MAX_LINKS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ArchShareRepository shares;
    private final ArchLinkRepository links;
    private final ArchProjectRepository projects;
    private final ArchVersionRepository versions;
    private final ArchitectureAccess access;
    private final ArchitectureService architecture;
    private final ArchitectureFiles files;
    private final FriendshipRepository friendships;
    private final GroupMemberRepository members;
    private final UserGroupRepository groups;
    private final UserRepository users;
    private final NotificationService notifications;
    private final ArchSceneService scene;
    private final Clock clock;

    public ArchShareService(ArchShareRepository shares, ArchLinkRepository links, ArchProjectRepository projects,
                            ArchVersionRepository versions, ArchitectureAccess access, ArchitectureService architecture,
                            ArchitectureFiles files, FriendshipRepository friendships, GroupMemberRepository members,
                            UserGroupRepository groups, UserRepository users, NotificationService notifications,
                            ArchSceneService scene, Clock clock) {
        this.scene = scene;
        this.shares = shares;
        this.links = links;
        this.projects = projects;
        this.versions = versions;
        this.access = access;
        this.architecture = architecture;
        this.files = files;
        this.friendships = friendships;
        this.members = members;
        this.groups = groups;
        this.users = users;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Shares list(UUID projectId, UUID userId) {
        access.require(projectId, userId, ArchRole.OWNER);
        List<ArchShare> all = shares.findByProjectIdOrderByCreatedAtAsc(projectId);
        Map<UUID, UserSummary> people = architecture.summaries(all.stream().map(ArchShare::getUserId)
                .filter(id -> id != null).toList());
        Map<UUID, UserGroup> byId = groupsById(all.stream().map(ArchShare::getGroupId).filter(id -> id != null).toList());
        return new Shares(
                all.stream().filter(s -> s.getUserId() != null && people.containsKey(s.getUserId()))
                        .map(s -> new UserShare(people.get(s.getUserId()), s.getRole())).toList(),
                all.stream().filter(s -> s.getGroupId() != null && byId.containsKey(s.getGroupId()))
                        .map(s -> new GroupShare(ref(byId.get(s.getGroupId())), s.getRole())).toList(),
                links.findByProjectIdAndExpiresAtAfterOrderByCreatedAtDesc(projectId, now()).stream().map(ArchShareService::link)
                        .toList());
    }

    /** Amigos e grupos do dono que ainda não receberam o projeto. */
    @Transactional(readOnly = true)
    public ShareOptions options(UUID projectId, UUID userId) {
        access.require(projectId, userId, ArchRole.OWNER);
        List<ArchShare> all = shares.findByProjectIdOrderByCreatedAtAsc(projectId);
        Set<UUID> sharedUsers = all.stream().map(ArchShare::getUserId).filter(id -> id != null).collect(Collectors.toSet());
        Set<UUID> sharedGroups = all.stream().map(ArchShare::getGroupId).filter(id -> id != null).collect(Collectors.toSet());
        List<UUID> friendIds = friendships.involving(userId, Friendship.Status.ACCEPTED).stream()
                .map(f -> f.other(userId)).filter(id -> !sharedUsers.contains(id)).toList();
        List<UserSummary> friends = users.findAllById(friendIds).stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).map(UserSummary::from).toList();
        List<GroupRef> myGroups = groupsById(members.findByUserId(userId).stream().map(GroupMember::getGroupId)
                .filter(id -> !sharedGroups.contains(id)).toList()).values().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).map(ArchShareService::ref).toList();
        return new ShareOptions(friends, myGroups);
    }

    /** Compartilha com um amigo (ou muda o papel); avisa só na primeira vez. */
    @Transactional
    public void shareWithUser(UUID projectId, UUID userId, UUID friendId, ArchRole role) {
        ArchProject project = access.require(projectId, userId, ArchRole.OWNER).project();
        requireShareable(role);
        if (friendships.between(userId, friendId).filter(f -> !f.isPending()).isEmpty()) {
            throw ArchitectureService.bad("Só dá para compartilhar com amigos.");
        }
        ArchShare existing = shares.findByProjectIdAndUserId(projectId, friendId).orElse(null);
        if (existing != null) {
            existing.setRole(role);
            return;
        }
        requireRoom(projectId);
        shares.save(ArchShare.withUser(projectId, friendId, role, userId));
        notifications.notify(friendId, NotificationType.ARCHITECTURE_SHARED, userId, null, data(project, userId, role));
    }

    @Transactional
    public void unshareUser(UUID projectId, UUID userId, UUID friendId) {
        access.require(projectId, userId, ArchRole.OWNER);
        shares.delete(shares.findByProjectIdAndUserId(projectId, friendId)
                .orElseThrow(() -> new NotFoundException("Compartilhamento não encontrado.")));
    }

    /** Quem recebeu direto pode sair do projeto (grupo: só saindo do grupo ou pelo dono). */
    @Transactional
    public void leave(UUID projectId, UUID userId) {
        shares.delete(shares.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("Projeto não encontrado.")));
    }

    /** Compartilha com um grupo de que o dono participa; avisa os membros (menos o dono) na primeira vez. */
    @Transactional
    public void shareWithGroup(UUID projectId, UUID userId, UUID groupId, ArchRole role) {
        ArchProject project = access.require(projectId, userId, ArchRole.OWNER).project();
        requireShareable(role);
        if (!members.existsByGroupIdAndUserId(groupId, userId)) throw ArchitectureService.bad("Grupo inválido.");
        ArchShare existing = shares.findByProjectIdAndGroupId(projectId, groupId).orElse(null);
        if (existing != null) {
            existing.setRole(role);
            return;
        }
        requireRoom(projectId);
        shares.save(ArchShare.withGroup(projectId, groupId, role, userId));
        UserGroup group = groups.findById(groupId).orElseThrow(() -> ArchitectureService.bad("Grupo inválido."));
        Map<String, String> data = new HashMap<>(data(project, userId, role));
        data.put("groupName", group.getName());
        for (GroupMember member : members.findByGroupIdOrderByCreatedAtAsc(groupId)) {
            if (member.getUserId().equals(userId)) continue;
            notifications.notify(member.getUserId(), NotificationType.ARCHITECTURE_SHARED, userId, groupId, data);
        }
    }

    @Transactional
    public void unshareGroup(UUID projectId, UUID userId, UUID groupId) {
        access.require(projectId, userId, ArchRole.OWNER);
        shares.delete(shares.findByProjectIdAndGroupId(projectId, groupId)
                .orElseThrow(() -> new NotFoundException("Compartilhamento não encontrado.")));
    }

    @Transactional
    public LinkResponse createLink(UUID projectId, UUID userId, int days) {
        access.require(projectId, userId, ArchRole.OWNER);
        if (links.countByProjectIdAndExpiresAtAfter(projectId, now()) >= MAX_LINKS) {
            throw ArchitectureService.bad("Limite de " + MAX_LINKS + " links ativos. Apague um antes.");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return link(links.save(new ArchLink(projectId, token, now().plusDays(days), userId)));
    }

    @Transactional
    public void deleteLink(UUID projectId, UUID userId, UUID linkId) {
        access.require(projectId, userId, ArchRole.OWNER);
        links.delete(links.findByIdAndProjectId(linkId, projectId)
                .orElseThrow(() -> new NotFoundException("Link não encontrado.")));
    }

    /** O que o link público mostra; link vencido, apagado ou projeto sem maquete → 404. */
    @Transactional(readOnly = true)
    public PublicProject publicProject(String token) {
        ArchLink link = validLink(token);
        ArchProject project = projects.findById(link.getProjectId()).orElseThrow(ArchShareService::linkNotFound);
        ArchVersion version = currentVersion(project);
        String owner = users.findById(project.getUserId()).map(User::getName).orElse(null);
        return new PublicProject(project.getName(), project.getDescription(), owner, project.getLatitude(),
                project.getNorthDegrees(), new PublicVersion(version.getNumber(), version.getLabel(), version.getSizeBytes(),
                "/api/public/architecture/" + token + "/model"), architecture.viewResponses(project.getId()),
                scene.sceneOf(version.getId()), link.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public Resource publicModel(String token) {
        ArchProject project = projects.findById(validLink(token).getProjectId()).orElseThrow(ArchShareService::linkNotFound);
        return files.resource(currentVersion(project).getFileKey(), Type.MODEL);
    }

    /** Apaga links vencidos há mais de 30 dias (os vencidos já não abrem). */
    @Transactional
    public void purgeExpiredLinks() {
        links.deleteByExpiresAtBefore(now().minusDays(30));
    }

    private ArchLink validLink(String token) {
        if (token == null || token.length() > 64) throw linkNotFound();
        return links.findByToken(token).filter(l -> l.getExpiresAt().isAfter(now())).orElseThrow(ArchShareService::linkNotFound);
    }

    private ArchVersion currentVersion(ArchProject project) {
        if (project.getCurrentVersionId() == null) throw linkNotFound();
        return versions.findById(project.getCurrentVersionId()).filter(ArchVersion::isReady)
                .orElseThrow(ArchShareService::linkNotFound);
    }

    private void requireRoom(UUID projectId) {
        if (shares.countByProjectId(projectId) >= MAX_SHARES) {
            throw ArchitectureService.bad("Limite de " + MAX_SHARES + " compartilhamentos por projeto.");
        }
    }

    private Map<UUID, UserGroup> groupsById(List<UUID> ids) {
        return groups.findAllById(ids).stream().collect(Collectors.toMap(UserGroup::getId, Function.identity()));
    }

    private Map<String, String> data(ArchProject project, UUID actorId, ArchRole role) {
        User actor = users.findById(actorId).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        return Map.of("actorName", actor.getName(),
                "actorUsername", actor.getUsername() == null ? "" : actor.getUsername(),
                "projectId", project.getId().toString(),
                "projectName", project.getName(),
                "role", role.name());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static void requireShareable(ArchRole role) {
        if (role == ArchRole.OWNER) throw ArchitectureService.bad("Permissão inválida.");
    }

    private static GroupRef ref(UserGroup group) {
        return new GroupRef(group.getId(), group.getName(), group.imageUrl());
    }

    private static LinkResponse link(ArchLink link) {
        return new LinkResponse(link.getId(), link.getToken(), link.getExpiresAt(), link.getCreatedAt());
    }

    private static NotFoundException linkNotFound() {
        return new NotFoundException("Link inválido ou vencido.");
    }
}
