package com.lifepedia.api.price;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceWatchRepository extends JpaRepository<PriceWatch, UUID> {

    List<PriceWatch> findByUserIdAndGroupIdIsNullOrderByCreatedAtDesc(UUID userId);

    List<PriceWatch> findByGroupIdInOrderByCreatedAtDesc(Collection<UUID> groupIds);

    List<PriceWatch> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<PriceWatch> findByPlanItemId(UUID planItemId);

    List<PriceWatch> findByPlanItemIdIn(Collection<UUID> planItemIds);

    long countByUserId(UUID userId);

    long countByLastCheckedAtAfter(OffsetDateTime since);

    /** Ativos sem consulta desde {@code before}, os mais antigos primeiro. */
    @Query("select w.id from PriceWatch w where w.active = true and (w.lastCheckedAt is null or w.lastCheckedAt < :before) "
            + "order by w.lastCheckedAt asc nulls first")
    List<UUID> due(OffsetDateTime before, Pageable pageable);

    @Query("select w.imageKey from PriceWatch w where w.userId = :userId and w.imageKey is not null")
    List<String> imageKeysOf(UUID userId);

    @Query("select w.imageKey from PriceWatch w where w.groupId = :groupId and w.imageKey is not null")
    List<String> imageKeysOfGroup(UUID groupId);
}
