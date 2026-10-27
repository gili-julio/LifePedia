package com.lifepedia.api.group;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Marca a última atividade de um grupo (sem mexer no {@code updated_at}). */
public interface GroupActivity extends Repository<UserGroup, UUID> {

    @Transactional
    @Modifying
    @Query("update UserGroup g set g.lastActivityAt = :at where g.id = :groupId")
    void touch(UUID groupId, OffsetDateTime at);
}
