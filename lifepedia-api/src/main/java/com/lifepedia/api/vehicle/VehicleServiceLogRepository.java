package com.lifepedia.api.vehicle;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehicleServiceLogRepository extends JpaRepository<VehicleServiceLog, UUID> {

    List<VehicleServiceLog> findByVehicleIdOrderByDoneOnDescCreatedAtDesc(UUID vehicleId, Pageable page);
}
