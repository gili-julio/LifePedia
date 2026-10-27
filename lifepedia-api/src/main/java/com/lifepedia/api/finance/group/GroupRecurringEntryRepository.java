package com.lifepedia.api.finance.group;

import com.lifepedia.api.finance.EntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRecurringEntryRepository extends JpaRepository<GroupRecurringEntry, UUID> {

    /** Ordenação por expressões com {@code r} (item), {@code c} (categoria) e {@code u} (quem levou). */
    @Query(value = "select r from GroupRecurringEntry r join Category c on c.id = r.categoryId join User u on u.id = r.userId "
            + "where r.groupId = :groupId and (:type is null or r.type = :type)",
            countQuery = "select count(r) from GroupRecurringEntry r where r.groupId = :groupId and (:type is null or r.type = :type)")
    Page<GroupRecurringEntry> page(UUID groupId, EntryType type, Pageable pageable);

    Optional<GroupRecurringEntry> findByIdAndGroupId(UUID id, UUID groupId);

    List<GroupRecurringEntry> findByGroupId(UUID groupId);

    List<GroupRecurringEntry> findByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndSourceId(UUID groupId, UUID sourceId);

    long countByCategoryIdAndSourceIdIsNull(UUID categoryId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update GroupRecurringEntry r set r.description = :description, r.type = :type, r.categoryId = :categoryId, "
            + "r.amount = :amount, r.dayOfMonth = :dayOfMonth, r.startMonth = :startMonth, r.endMonth = :endMonth, "
            + "r.active = :active, r.notes = :notes where r.sourceId = :sourceId")
    int sync(UUID sourceId, String description, EntryType type, UUID categoryId, BigDecimal amount, Integer dayOfMonth,
             YearMonth startMonth, YearMonth endMonth, boolean active, String notes);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GroupRecurringEntry r where r.groupId = :groupId and r.userId = :userId")
    int deleteByMember(UUID groupId, UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update GroupRecurringEntry r set r.categoryId = :to where r.userId = :userId and r.categoryId = :from")
    int reassignCategory(UUID userId, UUID from, UUID to);

    long countByCategoryId(UUID categoryId);

    long countByUserIdAndSourceIdIsNotNull(UUID userId);

    /** Sincronizados viram cópias (o item pessoal vai ser apagado, o do grupo fica). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update group_recurring_entries set source_id = null where user_id = :userId and source_id is not null",
            nativeQuery = true)
    int detachSynced(UUID userId);
}
