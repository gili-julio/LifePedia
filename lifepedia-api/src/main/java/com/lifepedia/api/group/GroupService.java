package com.lifepedia.api.group;

import com.lifepedia.api.group.GroupDtos.GroupDetail;
import com.lifepedia.api.group.GroupDtos.GroupRequest;
import com.lifepedia.api.group.GroupDtos.GroupSummary;
import com.lifepedia.api.group.GroupDtos.MemberResponse;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.image.ImageFile;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private static final Collator PT_BR = Collator.getInstance(Locale.of("pt", "BR"));

    private final UserGroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupInviteRepository invites;
    private final UserRepository users;
    private final GroupAccess access;
    private final GroupImageStore images;
    private final ApplicationEventPublisher events;

    public GroupService(UserGroupRepository groups, GroupMemberRepository members, GroupInviteRepository invites,
                        UserRepository users, GroupAccess access, GroupImageStore images,
                        ApplicationEventPublisher events) {
        this.groups = groups;
        this.members = members;
        this.invites = invites;
        this.users = users;
        this.access = access;
        this.images = images;
        this.events = events;
    }

    /** Grupos do usuário, por nome. */
    @Transactional(readOnly = true)
    public List<GroupSummary> list(UUID userId) {
        Map<UUID, GroupMember> mine = members.findByUserId(userId).stream()
                .collect(Collectors.toMap(GroupMember::getGroupId, Function.identity()));
        if (mine.isEmpty()) return List.of();
        Map<UUID, Long> counts = memberCounts(mine.keySet());
        return groups.findAllById(mine.keySet()).stream()
                .sorted(Comparator.comparing(UserGroup::getName, PT_BR))
                .map(g -> GroupSummary.of(g, counts.getOrDefault(g.getId(), 0L), mine.get(g.getId()).getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> memberCounts(Collection<UUID> groupIds) {
        return members.countByGroupIds(groupIds).stream()
                .collect(Collectors.toMap(GroupCount::getGroupId, GroupCount::getTotal));
    }

    @Transactional
    public GroupSummary create(UUID userId, GroupRequest request) {
        UserGroup group = groups.save(new UserGroup(request.name().trim(), userId));
        members.save(new GroupMember(group.getId(), userId, GroupMember.Role.ADMIN));
        return GroupSummary.of(group, 1, GroupMember.Role.ADMIN);
    }

    @Transactional(readOnly = true)
    public GroupDetail detail(UUID userId, UUID groupId) {
        GroupMember me = access.requireMember(groupId, userId);
        UserGroup group = access.group(groupId);
        List<GroupMember> all = members.findByGroupIdOrderByCreatedAtAsc(groupId);
        Map<UUID, User> byId = users.findAllById(all.stream().map(GroupMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<MemberResponse> list = all.stream()
                .sorted(Comparator.comparing((GroupMember m) -> m.isAdmin() ? 0 : 1)
                        .thenComparing(m -> byId.get(m.getUserId()).getName(), PT_BR))
                .map(m -> new MemberResponse(UserSummary.from(byId.get(m.getUserId())), m.getRole(), m.getCreatedAt()))
                .toList();
        return new GroupDetail(group.getId(), group.getName(), group.imageUrl(), me.getRole(), list);
    }

    @Transactional
    public GroupSummary rename(UUID userId, UUID groupId, GroupRequest request) {
        access.requireAdmin(groupId, userId);
        UserGroup group = access.group(groupId);
        group.setName(request.name().trim());
        return summary(group, GroupMember.Role.ADMIN);
    }

    @Transactional
    public void delete(UUID userId, UUID groupId) {
        access.requireAdmin(groupId, userId);
        UserGroup group = access.group(groupId);
        events.publishEvent(new GroupDeletedEvent(groupId));
        images.deleteAfterCommit(group.getImageKey());
        groups.delete(group);
    }

    @Transactional
    public GroupSummary setImage(UUID userId, UUID groupId, byte[] bytes) {
        access.requireAdmin(groupId, userId);
        UserGroup group = access.group(groupId);
        String previous = group.getImageKey();
        group.setImageKey(images.save(bytes));
        images.deleteAfterCommit(previous);
        return summary(group, GroupMember.Role.ADMIN);
    }

    @Transactional
    public GroupSummary removeImage(UUID userId, UUID groupId) {
        access.requireAdmin(groupId, userId);
        UserGroup group = access.group(groupId);
        images.deleteAfterCommit(group.getImageKey());
        group.setImageKey(null);
        return summary(group, GroupMember.Role.ADMIN);
    }

    /** Imagem para membros e convidados (a notificação do convite mostra a foto). */
    @Transactional(readOnly = true)
    public ImageFile image(UUID userId, UUID groupId) {
        if (!members.existsByGroupIdAndUserId(groupId, userId) && !invites.existsByGroupIdAndUserId(groupId, userId)) {
            throw new NotFoundException("Grupo não encontrado.");
        }
        return imageOf(access.group(groupId));
    }

    ImageFile imageOf(UserGroup group) {
        if (group.getImageKey() == null) throw new NotFoundException("O grupo não tem imagem.");
        return images.file(group.getImageKey());
    }

    /** Sai do grupo. Último membro: o grupo é excluído; último admin: o membro mais antigo vira admin. */
    @Transactional
    public Departure leave(UUID userId, UUID groupId) {
        GroupMember member = access.requireMember(groupId, userId);
        UserGroup group = access.group(groupId);
        removeMembership(member);
        List<GroupMember> remaining = members.findByGroupIdOrderByCreatedAtAsc(groupId);
        if (remaining.isEmpty()) {
            events.publishEvent(new GroupDeletedEvent(groupId));
            images.deleteAfterCommit(group.getImageKey());
            groups.delete(group);
            return new Departure(groupId, group.getName(), true, null, List.of());
        }
        UUID newAdmin = null;
        if (remaining.stream().noneMatch(GroupMember::isAdmin)) {
            remaining.getFirst().setRole(GroupMember.Role.ADMIN);
            newAdmin = remaining.getFirst().getUserId();
        }
        return new Departure(groupId, group.getName(), false, newAdmin,
                remaining.stream().map(GroupMember::getUserId).toList());
    }

    /** O que {@link #leave} faria em cada grupo do usuário (tela de excluir conta). */
    @Transactional(readOnly = true)
    public List<DeparturePreview> departurePreview(UUID userId) {
        List<GroupMember> mine = members.findByUserId(userId);
        Map<UUID, UserGroup> byId = groups.findAllById(mine.stream().map(GroupMember::getGroupId).toList()).stream()
                .collect(Collectors.toMap(UserGroup::getId, Function.identity()));
        return mine.stream().map(me -> {
            List<GroupMember> others = members.findByGroupIdOrderByCreatedAtAsc(me.getGroupId()).stream()
                    .filter(m -> !m.getUserId().equals(userId)).toList();
            UserSummary newAdmin = me.isAdmin() && !others.isEmpty() && others.stream().noneMatch(GroupMember::isAdmin)
                    ? users.findById(others.getFirst().getUserId()).map(UserSummary::from).orElse(null) : null;
            return new DeparturePreview(me.getGroupId(), byId.get(me.getGroupId()).getName(), others.size(),
                    others.isEmpty(), newAdmin);
        }).sorted(Comparator.comparing(DeparturePreview::name, PT_BR)).toList();
    }

    /** @param newAdminId quem virou admin (nulo = já havia outro); @param remaining membros que ficaram */
    public record Departure(UUID groupId, String groupName, boolean groupDeleted, UUID newAdminId, List<UUID> remaining) {
    }

    /** @param deletesGroup era o último membro; @param newAdmin quem vira admin (nulo = ninguém muda) */
    public record DeparturePreview(UUID groupId, String name, int otherMembers, boolean deletesGroup, UserSummary newAdmin) {
    }

    @Transactional
    public void removeMember(UUID adminId, UUID groupId, UUID userId) {
        access.requireAdmin(groupId, adminId);
        if (adminId.equals(userId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Para sair do grupo, use \"Sair do grupo\".");
        }
        removeMembership(members.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new NotFoundException("Membro não encontrado.")));
    }

    @Transactional
    public void setRole(UUID adminId, UUID groupId, UUID userId, GroupMember.Role role) {
        access.requireAdmin(groupId, adminId);
        GroupMember member = members.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new NotFoundException("Membro não encontrado."));
        if (member.isAdmin() && role != GroupMember.Role.ADMIN
                && members.countByGroupIdAndRole(groupId, GroupMember.Role.ADMIN) <= 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O grupo precisa de pelo menos um administrador.");
        }
        member.setRole(role);
    }

    private void removeMembership(GroupMember member) {
        events.publishEvent(new GroupMemberRemovedEvent(member.getGroupId(), member.getUserId()));
        members.delete(member);
        members.flush();
    }

    private GroupSummary summary(UserGroup group, GroupMember.Role role) {
        return GroupSummary.of(group, members.countByGroupId(group.getId()), role);
    }
}
