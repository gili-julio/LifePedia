package com.lifepedia.api.planning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanItemRepository extends JpaRepository<PlanItem, UUID> {

    List<PlanItem> findByGroupIdOrderByPositionAscCreatedAtAsc(UUID groupId);

    List<PlanItem> findByGroupIdAndUserId(UUID groupId, UUID userId);

    Optional<PlanItem> findByIdAndGroupId(UUID id, UUID groupId);

    long countByGroupId(UUID groupId);

    @Query("select coalesce(max(p.position), -1) from PlanItem p where p.groupId = :groupId")
    int maxPosition(UUID groupId);

    @Query("select p.imageKey from PlanItem p where p.groupId = :groupId and p.imageKey is not null")
    List<String> imageKeys(UUID groupId);
}
