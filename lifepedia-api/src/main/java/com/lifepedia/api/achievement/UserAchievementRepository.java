package com.lifepedia.api.achievement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, UUID> {

    List<UserAchievement> findByUserId(UUID userId);

    /** Grava o nível; já gravado (outra requisição chegou antes) fica como está. */
    @Modifying
    @Query(value = "insert into user_achievements (id, user_id, code, level, unlocked_at) "
            + "values (gen_random_uuid(), :userId, :code, :level, :at) on conflict do nothing", nativeQuery = true)
    void unlock(UUID userId, String code, int level, OffsetDateTime at);
}
