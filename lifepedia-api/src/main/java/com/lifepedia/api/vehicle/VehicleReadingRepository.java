package com.lifepedia.api.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleReadingRepository extends JpaRepository<VehicleReading, UUID> {

    Optional<VehicleReading> findByIdAndVehicleId(UUID id, UUID vehicleId);

    Optional<VehicleReading> findByExpenseId(UUID expenseId);

    List<VehicleReading> findByVehicleIdOrderByReadOnAscKmAsc(UUID vehicleId);

    List<VehicleReading> findByVehicleIdInOrderByReadOnAscKmAsc(Collection<UUID> vehicleIds);

    /** Maior km lido antes da data (sem contar {@code ignore}). */
    @Query("""
            select max(r.km) from VehicleReading r
            where r.vehicleId = :vehicleId and r.readOn < :date and (:ignore is null or r.id <> :ignore)""")
    Integer maxBefore(UUID vehicleId, LocalDate date, UUID ignore);

    /** Menor km lido depois da data (sem contar {@code ignore}). */
    @Query("""
            select min(r.km) from VehicleReading r
            where r.vehicleId = :vehicleId and r.readOn > :date and (:ignore is null or r.id <> :ignore)""")
    Integer minAfter(UUID vehicleId, LocalDate date, UUID ignore);
}
