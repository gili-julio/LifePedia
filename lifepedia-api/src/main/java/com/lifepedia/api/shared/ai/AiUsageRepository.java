package com.lifepedia.api.shared.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiUsageRepository extends JpaRepository<AiUsage, UUID> {
}
