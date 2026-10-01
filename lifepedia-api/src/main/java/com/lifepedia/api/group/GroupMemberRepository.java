package com.lifepedia.api.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    List<GroupMember> findByUserId(UUID userId);

    List<GroupMember> findByGroupIdOrderByCreatedAtAsc(UUID groupId);

    long countByGroupId(UUID groupId);

    long countByGroupIdAndRole(UUID groupId, GroupMember.Role role);

    @Query("select m.groupId as groupId, count(m) as total from GroupMember m "
            + "where m.groupId in :groupIds group by m.groupId")
    List<GroupCount> countByGroupIds(Collection<UUID> groupIds);
}
