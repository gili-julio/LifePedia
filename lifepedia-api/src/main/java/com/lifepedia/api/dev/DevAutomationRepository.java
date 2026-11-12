package com.lifepedia.api.dev;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DevAutomationRepository extends JpaRepository<DevAutomation, UUID> {

    List<DevAutomation> findByRepoId(UUID repoId);
}
