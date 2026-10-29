package com.lifepedia.api.notification.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    /** Aparelhos com sessão de login válida. */
    @Query("""
            select p from PushSubscription p
            where p.userId in :userIds
              and p.sessionId in (select s.id from UserSession s where s.expiresAt > :now)""")
    List<PushSubscription> findActive(Collection<UUID> userIds, Instant now);
}
