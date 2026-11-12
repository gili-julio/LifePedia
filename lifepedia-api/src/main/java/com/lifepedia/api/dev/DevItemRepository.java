package com.lifepedia.api.dev;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DevItemRepository extends JpaRepository<DevItem, UUID> {

    List<DevItem> findByRepoIdIn(Collection<UUID> repoIds);

    List<DevItem> findByUserId(UUID userId);

    Optional<DevItem> findFirstByTaskIdAndKind(UUID taskId, DevItem.Kind kind);
}
