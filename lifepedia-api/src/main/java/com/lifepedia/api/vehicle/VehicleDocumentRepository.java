package com.lifepedia.api.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, UUID> {

    Optional<VehicleDocument> findByIdAndVehicleId(UUID id, UUID vehicleId);

    List<VehicleDocument> findByVehicleIdOrderByDueOnAsc(UUID vehicleId);

    List<VehicleDocument> findByVehicleIdIn(Collection<UUID> vehicleIds);

    long countByVehicleId(UUID vehicleId);
}
