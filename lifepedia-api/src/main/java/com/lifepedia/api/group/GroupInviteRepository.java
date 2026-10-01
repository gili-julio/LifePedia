package com.lifepedia.api.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupInviteRepository extends JpaRepository<GroupInvite, UUID> {

    Optional<GroupInvite> findByIdAndUserId(UUID id, UUID userId);

    Optional<GroupInvite> findByIdAndGroupId(UUID id, UUID groupId);

    List<GroupInvite> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, GroupInvite.Status status);

    List<GroupInvite> findByGroupIdAndStatusOrderByCreatedAtDesc(UUID groupId, GroupInvite.Status status);

    List<GroupInvite> findByGroupIdAndUserIdAndStatus(UUID groupId, UUID userId, GroupInvite.Status status);

    long countByUserIdAndStatus(UUID userId, GroupInvite.Status status);

    /** Convidado em algum momento (vê a imagem do grupo na notificação). */
    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);
}
