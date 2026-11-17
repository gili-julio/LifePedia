package com.lifepedia.api.finance.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Card> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    List<Card> findByRemindersTrue();

    /** Cartões mostrados no grupo. */
    @Query("select c from Card c where :groupId member of c.groupIds order by c.createdAt")
    List<Card> sharedWith(UUID groupId);

    @Query("select c from Card c where c.userId = :userId and :groupId member of c.groupIds")
    List<Card> sharedBy(UUID userId, UUID groupId);
}
