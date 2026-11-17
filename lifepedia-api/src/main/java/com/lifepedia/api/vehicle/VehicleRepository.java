package com.lifepedia.api.vehicle;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findByUserIdOrderByArchivedAtAscCreatedAtAsc(UUID userId);

    List<Vehicle> findByIdInOrderByCreatedAtAsc(Collection<UUID> ids);

    long countByUserId(UUID userId);

    List<Vehicle> findByArchivedAtIsNull();

    /** Com referência na FIPE e valor mais velho que {@code before} (ou nunca lido). */
    @Query("""
            select v from Vehicle v
            where v.archivedAt is null and v.fipeKind is not null and v.fipeYear is not null
              and (v.fipeCheckedAt is null or v.fipeCheckedAt < :before)
            order by v.fipeCheckedAt nulls first, v.id""")
    List<Vehicle> fipeDue(OffsetDateTime before, Pageable page);

    @Query("select v.imageKey from Vehicle v where v.userId = :userId and v.imageKey is not null")
    List<String> imageKeysOf(UUID userId);
}
