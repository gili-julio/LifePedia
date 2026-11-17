package com.lifepedia.api.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleShareRepository extends JpaRepository<VehicleShare, UUID> {

    List<VehicleShare> findByVehicleIdOrderByCreatedAtAsc(UUID vehicleId);

    Optional<VehicleShare> findByVehicleIdAndUserId(UUID vehicleId, UUID userId);

    Optional<VehicleShare> findByVehicleIdAndGroupId(UUID vehicleId, UUID groupId);

    long countByVehicleId(UUID vehicleId);

    /** Quem sai do grupo deixa de compartilhar os veículos dele ali. */
    @Modifying
    @Query("delete from VehicleShare s where s.groupId = :groupId and s.vehicleId in (select v.id from Vehicle v where v.userId = :userId)")
    void deleteGroupSharesOf(UUID userId, UUID groupId);
}
