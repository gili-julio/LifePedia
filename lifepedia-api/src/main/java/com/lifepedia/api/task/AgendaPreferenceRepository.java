package com.lifepedia.api.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AgendaPreferenceRepository extends JpaRepository<AgendaPreference, UUID> {

    Optional<AgendaPreference> findByUserId(UUID userId);
}
