package com.lifepedia.api.delivery;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    /** Da pessoa ou compartilhadas com os grupos dela; ativas ({@code archived} false) ou arquivadas. */
    @Query("""
            select d from Delivery d
            where (d.userId = :userId or d.groupId in :groupIds)
              and ((:archived = true and d.archivedAt is not null) or (:archived = false and d.archivedAt is null))
            order by d.createdAt desc, d.id""")
    List<Delivery> visible(UUID userId, Collection<UUID> groupIds, boolean archived);

    Optional<Delivery> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndTrackingCode(UUID userId, String trackingCode);

    long countByUserIdAndArchivedAtIsNull(UUID userId);

    List<Delivery> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Delivery> findByUserIdAndGroupId(UUID userId, UUID groupId);

    /** Na vez de sincronizar: não arquivadas e não entregues, a mais antiga primeiro. */
    @Query("""
            select d from Delivery d
            where d.archivedAt is null and d.status <> :delivered and (d.lastSyncAt is null or d.lastSyncAt < :before)
            order by d.lastSyncAt asc nulls first, d.id""")
    List<Delivery> due(OffsetDateTime before, DeliveryStatus delivered, Pageable page);

    @Modifying
    @Query("""
            update Delivery d set d.archivedAt = :now
            where d.archivedAt is null and d.status = :delivered and d.deliveredAt < :before""")
    int archiveDelivered(OffsetDateTime before, OffsetDateTime now, DeliveryStatus delivered);

    @Query("select d.imageKey from Delivery d where d.userId = :userId and d.imageKey is not null")
    List<String> imageKeysOf(UUID userId);
}
