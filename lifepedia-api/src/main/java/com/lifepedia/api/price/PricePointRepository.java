package com.lifepedia.api.price;

import com.lifepedia.api.price.PricePoint.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PricePointRepository extends JpaRepository<PricePoint, UUID> {

    List<PricePoint> findByWatchIdAndDayGreaterThanEqualOrderByDayAsc(UUID watchId, LocalDate from);

    List<PricePoint> findByWatchIdAndSourceOrderByDayDesc(UUID watchId, Source source);

    List<PricePoint> findByWatchIdAndSourceAndDayGreaterThanEqual(UUID watchId, Source source, LocalDate from);

    Optional<PricePoint> findByWatchIdAndDayAndStoreAndSource(UUID watchId, LocalDate day, String store, Source source);

    Optional<PricePoint> findByIdAndWatchId(UUID id, UUID watchId);

    long countByWatchIdAndSource(UUID watchId, Source source);
}
