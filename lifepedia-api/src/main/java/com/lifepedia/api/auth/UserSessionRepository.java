package com.lifepedia.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    /** Sessão viva e conta não bloqueada. */
    @Query("select count(s) > 0 from UserSession s, User u where s.id = :id and s.expiresAt > :now "
            + "and u.id = s.userId and u.blockedAt is null")
    boolean isActive(UUID id, Instant now);

    long countByUserIdAndExpiresAtAfter(UUID userId, Instant now);

    @Transactional
    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :now")
    int deleteExpired(Instant now);

    @Modifying
    @Query("delete from UserSession s where s.userId = :userId")
    int deleteByUser(UUID userId);
}
