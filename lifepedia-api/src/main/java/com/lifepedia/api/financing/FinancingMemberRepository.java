package com.lifepedia.api.financing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancingMemberRepository extends JpaRepository<FinancingMember, UUID> {

    List<FinancingMember> findByFinancingIdOrderByCreatedAtAsc(UUID financingId);

    List<FinancingMember> findByFinancingIdIn(Collection<UUID> financingIds);

    List<FinancingMember> findByUserId(UUID userId);

    Optional<FinancingMember> findByFinancingIdAndUserId(UUID financingId, UUID userId);

    List<FinancingMember> findByRecurringEntryIdIn(Collection<UUID> recurringEntryIds);

    /** Quem sai do grupo deixa de pagar os financiamentos dele (o fixo pessoal fica, solto). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from FinancingMember m where m.userId = :userId "
            + "and m.financingId in (select f.id from Financing f where f.groupId = :groupId)")
    int deleteByGroupMember(UUID groupId, UUID userId);
}
