package com.lifepedia.api.price;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceLinkRepository extends JpaRepository<PriceLink, UUID> {

    List<PriceLink> findByWatchIdOrderByCreatedAtAsc(UUID watchId);

    List<PriceLink> findByWatchIdIn(Collection<UUID> watchIds);

    Optional<PriceLink> findByIdAndWatchId(UUID id, UUID watchId);

    long countByWatchId(UUID watchId);

    boolean existsByWatchIdAndUrl(UUID watchId, String url);
}
