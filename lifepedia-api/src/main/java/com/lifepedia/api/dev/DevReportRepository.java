package com.lifepedia.api.dev;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DevReportRepository extends JpaRepository<DevReport, UUID> {

    Optional<DevReport> findByProjectIdAndDay(UUID projectId, LocalDate day);

    List<DevReport> findByProjectIdAndDayBetweenOrderByDayDesc(UUID projectId, LocalDate from, LocalDate to);

    List<DevReport> findByUserIdAndDay(UUID userId, LocalDate day);

    List<DevReport> findByUserIdOrderByDayAsc(UUID userId);
}
