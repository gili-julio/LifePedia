package com.lifepedia.api.dev;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DevActivityRepository extends JpaRepository<DevActivity, UUID> {

    List<DevActivity> findByUserIdAndDayGreaterThanEqualOrderByDayAsc(UUID userId, LocalDate from);

    List<DevActivity> findByRepoIdInAndDayGreaterThanEqual(Collection<UUID> repoIds, LocalDate from);
}
