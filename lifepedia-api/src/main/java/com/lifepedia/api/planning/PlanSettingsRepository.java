package com.lifepedia.api.planning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanSettingsRepository extends JpaRepository<PlanSettings, UUID> {

    Optional<PlanSettings> findByGroupId(UUID groupId);
}
