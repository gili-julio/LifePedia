package com.lifepedia.api.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRevisionRepository extends JpaRepository<VehicleRevision, UUID> {

    Optional<VehicleRevision> findByIdAndVehicleId(UUID id, UUID vehicleId);

    List<VehicleRevision> findByVehicleIdOrderByDueKmAscDueOnAscCreatedAtAsc(UUID vehicleId);

    List<VehicleRevision> findByVehicleIdIn(Collection<UUID> vehicleIds);

    long countByVehicleId(UUID vehicleId);
}
