package com.lifepedia.api.finance.transaction;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.category.CategoryUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByUserIdAndMonthOrderByDateDescCreatedAtDesc(UUID userId, YearMonth month);

    /** Ordenação por expressões com {@code t} (lançamento) e {@code c} (categoria). */
    @Query(value = "select t from Transaction t join Category c on c.id = t.categoryId "
            + "where t.userId = :userId and t.month = :month and (:type is null or t.type = :type)",
            countQuery = "select count(t) from Transaction t "
                    + "where t.userId = :userId and t.month = :month and (:type is null or t.type = :type)")
    Page<Transaction> page(UUID userId, YearMonth month, EntryType type, Pageable pageable);

    /** @param description já normalizada (minúsculas, sem espaços nas pontas) */
    @Query("select t from Transaction t where t.userId = :userId and t.month >= :from and t.month <= :to "
            + "and (:type is null or t.type = :type) "
            + "and (:description is null or lower(trim(t.description)) = :description) "
            + "order by t.date desc, t.createdAt desc")
    List<Transaction> search(UUID userId, YearMonth from, YearMonth to, EntryType type, String description);

    List<Transaction> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    /** Ocasionais do mês na categoria, agrupados pela descrição. */
    @Query("select min(t.description) as description, count(t) as count, sum(t.amount) as total from Transaction t "
            + "where t.userId = :userId and t.month = :month and t.categoryId = :categoryId "
            + "group by lower(trim(t.description))")
    List<DescriptionGroup> groupByDescription(UUID userId, YearMonth month, UUID categoryId);

    /** Descrições desde {@code from}, as mais usadas primeiro. */
    @Query("select min(t.description) from Transaction t where t.userId = :userId and t.month >= :from "
            + "group by lower(trim(t.description)) order by count(t) desc, max(t.date) desc")
    List<String> topDescriptions(UUID userId, YearMonth from, Pageable pageable);

    List<Transaction> findByUserIdAndMonthBetween(UUID userId, YearMonth from, YearMonth to);

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    @Query("select t.categoryId as categoryId, count(t) as total from Transaction t "
            + "where t.userId = :userId group by t.categoryId")
    List<CategoryUsage> countByCategory(UUID userId);

    long countByCategoryId(UUID categoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Transaction t set t.categoryId = :to where t.userId = :userId and t.categoryId = :from")
    int reassignCategory(UUID userId, UUID from, UUID to);

    long countByUserId(UUID userId);

    List<Transaction> findByUserIdOrderByDateAscCreatedAtAsc(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Transaction t where t.userId = :userId")
    int deleteAllOfUser(UUID userId);
}
