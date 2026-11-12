package com.lifepedia.api.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryAddressRepository extends JpaRepository<DeliveryAddress, UUID> {

    @Query("select a from DeliveryAddress a where a.userId = :userId order by a.defaultAddress desc, a.label asc")
    List<DeliveryAddress> listOf(UUID userId);

    Optional<DeliveryAddress> findByIdAndUserId(UUID id, UUID userId);

    List<DeliveryAddress> findByIdIn(Collection<UUID> ids);

    long countByUserId(UUID userId);

    @Modifying
    @Query("update DeliveryAddress a set a.defaultAddress = false where a.userId = :userId and a.id <> :keep")
    void clearDefault(UUID userId, UUID keep);
}
