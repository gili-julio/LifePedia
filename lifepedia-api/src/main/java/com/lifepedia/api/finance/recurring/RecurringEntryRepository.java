package com.lifepedia.api.finance.recurring;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.category.CategoryUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringEntryRepository extends JpaRepository<RecurringEntry, UUID> {

    List<RecurringEntry> findByUserIdOrderByTypeAscAmountDesc(UUID userId);

    List<RecurringEntry> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    List<RecurringEntry> findByUserIdAndTypeOrderByAmountDesc(UUID userId, EntryType type);

    Optional<RecurringEntry> findByIdAndUserId(UUID id, UUID userId);

    /** Ordenação por expressões com {@code r} (fixo) e {@code c} (categoria). */
    @Query(value = "select r from RecurringEntry r join Category c on c.id = r.categoryId "
            + "where r.userId = :userId and (:type is null or r.type = :type)",
            countQuery = "select count(r) from RecurringEntry r where r.userId = :userId and (:type is null or r.type = :type)")
    Page<RecurringEntry> page(UUID userId, EntryType type, Pageable pageable);

    @Query("select r.categoryId as categoryId, count(r) as total from RecurringEntry r "
            + "where r.userId = :userId group by r.categoryId")
    List<CategoryUsage> countByCategory(UUID userId);

    long countByCategoryId(UUID categoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RecurringEntry r set r.categoryId = :to where r.userId = :userId and r.categoryId = :from")
    int reassignCategory(UUID userId, UUID from, UUID to);

    long countByUserId(UUID userId);

    /** Os ajustes mensais saem junto (FK em cascata). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RecurringEntry r where r.userId = :userId")
    int deleteAllOfUser(UUID userId);
}
