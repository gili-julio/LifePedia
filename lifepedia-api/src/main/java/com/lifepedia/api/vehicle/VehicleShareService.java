package com.lifepedia.api.vehicle;

import com.lifepedia.api.friend.Friendship;
import com.lifepedia.api.friend.FriendshipRepository;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.group.UserGroup;
import com.lifepedia.api.group.UserGroupRepository;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import com.lifepedia.api.vehicle.VehicleDtos.GroupRef;
import com.lifepedia.api.vehicle.VehicleDtos.GroupShare;
import com.lifepedia.api.vehicle.VehicleDtos.ShareOptions;
import com.lifepedia.api.vehicle.VehicleDtos.Shares;
import com.lifepedia.api.vehicle.VehicleDtos.UserShare;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Compartilhamento (só o dono) com amigos e com grupos de que o dono participa; quem recebe é avisado. */
@Service
public class VehicleShareService {

    static final int MAX_SHARES = 30;

    private final VehicleShareRepository shares;
    private final VehicleAccess access;
    private final FriendshipRepository friendships;
    private final GroupMemberRepository members;
    private final UserGroupRepository groups;
    private final UserRepository users;
    private final NotificationService notifications;

    public VehicleShareService(VehicleShareRepository shares, VehicleAccess access, FriendshipRepository friendships,
                               GroupMemberRepository members, UserGroupRepository groups, UserRepository users,
                               NotificationService notifications) {
        this.shares = shares;
        this.access = access;
        this.friendships = friendships;
        this.members = members;
        this.groups = groups;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public Shares list(UUID vehicleId, UUID userId) {
        access.require(vehicleId, userId, VehicleRole.OWNER);
        List<VehicleShare> all = shares.findByVehicleIdOrderByCreatedAtAsc(vehicleId);
        Map<UUID, User> people = users.findAllById(all.stream().map(VehicleShare::getUserId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, UserGroup> byId = groupsById(all.stream().map(VehicleShare::getGroupId).filter(Objects::nonNull).toList());
        return new Shares(
                all.stream().filter(s -> s.getUserId() != null && people.containsKey(s.getUserId()))
                        .map(s -> new UserShare(UserSummary.from(people.get(s.getUserId())), s.getRole())).toList(),
                all.stream().filter(s -> s.getGroupId() != null && byId.containsKey(s.getGroupId()))
                        .map(s -> new GroupShare(ref(byId.get(s.getGroupId())), s.getRole())).toList());
    }

    /** Amigos e grupos do dono que ainda não receberam o veículo. */
    @Transactional(readOnly = true)
    public ShareOptions options(UUID vehicleId, UUID userId) {
        access.require(vehicleId, userId, VehicleRole.OWNER);
        List<VehicleShare> all = shares.findByVehicleIdOrderByCreatedAtAsc(vehicleId);
        Set<UUID> sharedUsers = all.stream().map(VehicleShare::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> sharedGroups = all.stream().map(VehicleShare::getGroupId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<UUID> friendIds = friendships.involving(userId, Friendship.Status.ACCEPTED).stream()
                .map(f -> f.other(userId)).filter(id -> !sharedUsers.contains(id)).toList();
        List<UserSummary> friends = users.findAllById(friendIds).stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).map(UserSummary::from).toList();
        List<GroupRef> myGroups = groupsById(members.findByUserId(userId).stream().map(GroupMember::getGroupId)
                .filter(id -> !sharedGroups.contains(id)).toList()).values().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).map(VehicleShareService::ref).toList();
        return new ShareOptions(friends, myGroups);
    }

    /** Com um amigo (ou muda a permissão); avisa só na primeira vez. */
    @Transactional
    public void shareWithUser(UUID vehicleId, UUID userId, UUID friendId, VehicleRole role) {
        Vehicle vehicle = access.require(vehicleId, userId, VehicleRole.OWNER).vehicle();
        requireShareable(role);
        if (friendships.between(userId, friendId).filter(f -> !f.isPending()).isEmpty()) {
            throw bad("Só dá para compartilhar com amigos.");
        }
        VehicleShare existing = shares.findByVehicleIdAndUserId(vehicleId, friendId).orElse(null);
        if (existing != null) {
            existing.setRole(role);
            return;
        }
        requireRoom(vehicleId);
        shares.save(VehicleShare.withUser(vehicleId, friendId, role, userId));
        notifications.notify(friendId, NotificationType.VEHICLE_SHARED, userId, null, data(vehicle, userId, role));
    }

    @Transactional
    public void unshareUser(UUID vehicleId, UUID userId, UUID friendId) {
        access.require(vehicleId, userId, VehicleRole.OWNER);
        shares.delete(shares.findByVehicleIdAndUserId(vehicleId, friendId)
                .orElseThrow(() -> new NotFoundException("Compartilhamento não encontrado.")));
    }

    /** Quem recebeu direto pode sair (por grupo: só saindo do grupo ou pelo dono). */
    @Transactional
    public void leave(UUID vehicleId, UUID userId) {
        shares.delete(shares.findByVehicleIdAndUserId(vehicleId, userId).orElseThrow(VehicleAccess::notFound));
    }

    /** Com um grupo de que o dono participa; avisa os membros (menos o dono) na primeira vez. */
    @Transactional
    public void shareWithGroup(UUID vehicleId, UUID userId, UUID groupId, VehicleRole role) {
        Vehicle vehicle = access.require(vehicleId, userId, VehicleRole.OWNER).vehicle();
        requireShareable(role);
        if (!members.existsByGroupIdAndUserId(groupId, userId)) throw bad("Grupo inválido.");
        VehicleShare existing = shares.findByVehicleIdAndGroupId(vehicleId, groupId).orElse(null);
        if (existing != null) {
            existing.setRole(role);
            return;
        }
        requireRoom(vehicleId);
        shares.save(VehicleShare.withGroup(vehicleId, groupId, role, userId));
        UserGroup group = groups.findById(groupId).orElseThrow(() -> bad("Grupo inválido."));
        Map<String, String> data = new HashMap<>(data(vehicle, userId, role));
        data.put("groupName", group.getName());
        for (GroupMember member : members.findByGroupIdOrderByCreatedAtAsc(groupId)) {
            if (member.getUserId().equals(userId)) continue;
            notifications.notify(member.getUserId(), NotificationType.VEHICLE_SHARED, userId, groupId, data);
        }
    }

    @Transactional
    public void unshareGroup(UUID vehicleId, UUID userId, UUID groupId) {
        access.require(vehicleId, userId, VehicleRole.OWNER);
        shares.delete(shares.findByVehicleIdAndGroupId(vehicleId, groupId)
                .orElseThrow(() -> new NotFoundException("Compartilhamento não encontrado.")));
    }

    private void requireRoom(UUID vehicleId) {
        if (shares.countByVehicleId(vehicleId) >= MAX_SHARES) throw bad("Limite de " + MAX_SHARES + " compartilhamentos por veículo.");
    }

    private Map<UUID, UserGroup> groupsById(List<UUID> ids) {
        return groups.findAllById(ids).stream().collect(Collectors.toMap(UserGroup::getId, Function.identity()));
    }

    private Map<String, String> data(Vehicle vehicle, UUID actorId, VehicleRole role) {
        User actor = users.findById(actorId).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        return Map.of("actorName", actor.getName(),
                "actorUsername", actor.getUsername() == null ? "" : actor.getUsername(),
                "vehicleId", vehicle.getId().toString(),
                "vehicleName", vehicle.getName(),
                "role", role.name());
    }

    private static void requireShareable(VehicleRole role) {
        if (role == VehicleRole.OWNER) throw bad("Permissão inválida.");
    }

    private static GroupRef ref(UserGroup group) {
        return new GroupRef(group.getId(), group.getName(), group.imageUrl());
    }

    private static BusinessException bad(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}
