package com.lifepedia.api.friend;

import com.lifepedia.api.friend.Friendship.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("select f from Friendship f where (f.requesterId = :a and f.addresseeId = :b) "
            + "or (f.requesterId = :b and f.addresseeId = :a)")
    Optional<Friendship> between(UUID a, UUID b);

    @Query("select f from Friendship f where f.status = :status and (f.requesterId = :userId or f.addresseeId = :userId)")
    List<Friendship> involving(UUID userId, Status status);

    @Query("select count(f) from Friendship f where f.status = :status "
            + "and (f.requesterId = :userId or f.addresseeId = :userId)")
    long countInvolving(UUID userId, Status status);

    /** Ligações do usuário com cada pessoa de {@code others} (busca). */
    @Query("select f from Friendship f where (f.requesterId = :userId and f.addresseeId in :others) "
            + "or (f.addresseeId = :userId and f.requesterId in :others)")
    List<Friendship> withAny(UUID userId, Collection<UUID> others);

    List<Friendship> findByAddresseeIdAndStatusOrderByCreatedAtDesc(UUID addresseeId, Status status);

    List<Friendship> findByRequesterIdAndStatusOrderByCreatedAtDesc(UUID requesterId, Status status);

    long countByRequesterIdAndStatus(UUID requesterId, Status status);

    long countByAddresseeIdAndStatus(UUID addresseeId, Status status);
}
