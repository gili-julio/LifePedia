package com.lifepedia.api.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GroupInviteLinkRepository extends JpaRepository<GroupInviteLink, UUID> {

    Optional<GroupInviteLink> findByGroupId(UUID groupId);

    Optional<GroupInviteLink> findByToken(String token);
}
