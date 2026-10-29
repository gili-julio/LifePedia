package com.lifepedia.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    boolean existsByIdAndExpiresAtAfter(UUID id, Instant now);

    @Transactional
    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :now")
    int deleteExpired(Instant now);
}
