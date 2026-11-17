package com.lifepedia.api.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleExpenseRepository extends JpaRepository<VehicleExpense, UUID> {

    Optional<VehicleExpense> findByIdAndVehicleId(UUID id, UUID vehicleId);

    List<VehicleExpense> findByVehicleIdAndSpentOnGreaterThanEqualOrderBySpentOnDescCreatedAtDesc(UUID vehicleId, LocalDate from);

    List<VehicleExpense> findByVehicleIdAndKindOrderBySpentOnAscOdometerAsc(UUID vehicleId, ExpenseKind kind);

    List<VehicleExpense> findByVehicleIdOrderBySpentOnAscCreatedAtAsc(UUID vehicleId);

    @Query("""
            select e.imageKey from VehicleExpense e
            where e.imageKey is not null and e.vehicleId in (select v.id from Vehicle v where v.userId = :userId)""")
    List<String> imageKeysOfOwner(UUID userId);

    @Query("select e.imageKey from VehicleExpense e where e.vehicleId = :vehicleId and e.imageKey is not null")
    List<String> imageKeysOfVehicle(UUID vehicleId);
}
