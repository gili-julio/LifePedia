package com.lifepedia.api.finance.group;

import com.lifepedia.api.finance.EntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupTransactionRepository extends JpaRepository<GroupTransaction, UUID> {

    /** Ordenação por expressões com {@code g} (item), {@code c} (categoria) e {@code u} (quem levou). */
    @Query(value = "select g from GroupTransaction g join Category c on c.id = g.categoryId join User u on u.id = g.userId "
            + "where g.groupId = :groupId and g.month = :month and (:type is null or g.type = :type)",
            countQuery = "select count(g) from GroupTransaction g "
                    + "where g.groupId = :groupId and g.month = :month and (:type is null or g.type = :type)")
    Page<GroupTransaction> page(UUID groupId, YearMonth month, EntryType type, Pageable pageable);

    Optional<GroupTransaction> findByIdAndGroupId(UUID id, UUID groupId);

    List<GroupTransaction> findByGroupIdAndMonth(UUID groupId, YearMonth month);

    List<GroupTransaction> findByGroupIdAndMonthBetween(UUID groupId, YearMonth from, YearMonth to);

    List<GroupTransaction> findByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndSourceId(UUID groupId, UUID sourceId);

    long countByCategoryIdAndSourceIdIsNull(UUID categoryId);

    /** Espelha a edição do item pessoal nos grupos em que está sincronizado. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update GroupTransaction g set g.description = :description, g.type = :type, g.categoryId = :categoryId, "
            + "g.amount = :amount, g.date = :date, g.month = :month, g.notes = :notes where g.sourceId = :sourceId")
    int sync(UUID sourceId, String description, EntryType type, UUID categoryId, BigDecimal amount, LocalDate date,
             YearMonth month, String notes);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GroupTransaction g where g.groupId = :groupId and g.userId = :userId")
    int deleteByMember(UUID groupId, UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update GroupTransaction g set g.categoryId = :to where g.userId = :userId and g.categoryId = :from")
    int reassignCategory(UUID userId, UUID from, UUID to);

    long countByCategoryId(UUID categoryId);

    long countByUserIdAndSourceIdIsNotNull(UUID userId);

    /** Sincronizados viram cópias (o item pessoal vai ser apagado, o do grupo fica). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update group_transactions set source_id = null where user_id = :userId and source_id is not null", nativeQuery = true)
    int detachSynced(UUID userId);
}
