package com.lifepedia.api.group;

import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Checagens de acesso a grupos. Quem não é membro recebe 404 (não revela que o grupo existe). */
@Component
public class GroupAccess {

    private final UserGroupRepository groups;
    private final GroupMemberRepository members;

    public GroupAccess(UserGroupRepository groups, GroupMemberRepository members) {
        this.groups = groups;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public GroupMember requireMember(UUID groupId, UUID userId) {
        return members.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new NotFoundException("Grupo não encontrado."));
    }

    @Transactional(readOnly = true)
    public GroupMember requireAdmin(UUID groupId, UUID userId) {
        GroupMember member = requireMember(groupId, userId);
        if (!member.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Só administradores do grupo podem fazer isso.");
        }
        return member;
    }

    @Transactional(readOnly = true)
    public UserGroup group(UUID groupId) {
        return groups.findById(groupId).orElseThrow(() -> new NotFoundException("Grupo não encontrado."));
    }
}
