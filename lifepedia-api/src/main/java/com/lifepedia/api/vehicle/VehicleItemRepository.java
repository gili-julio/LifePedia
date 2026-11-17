package com.lifepedia.api.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleItemRepository extends JpaRepository<VehicleItem, UUID> {

    Optional<VehicleItem> findByIdAndVehicleId(UUID id, UUID vehicleId);

    List<VehicleItem> findByVehicleIdOrderByPositionAscCreatedAtAsc(UUID vehicleId);

    List<VehicleItem> findByVehicleIdIn(Collection<UUID> vehicleIds);

    List<VehicleItem> findByVehicleIdAndIdIn(UUID vehicleId, Collection<UUID> ids);

    long countByVehicleId(UUID vehicleId);
}
