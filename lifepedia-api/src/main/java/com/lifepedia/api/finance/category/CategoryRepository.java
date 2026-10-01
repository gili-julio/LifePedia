package com.lifepedia.api.finance.category;

import com.lifepedia.api.finance.EntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByUserId(UUID userId);

    List<Category> findByUserIdAndType(UUID userId, EntryType type);

    /** Uso = fixos + ocasionais na categoria. */
    String USAGE = "((select count(t) from Transaction t where t.categoryId = c.id) "
            + "+ (select count(r) from RecurringEntry r where r.categoryId = c.id))";

    @Query(value = "select c from Category c where c.userId = :userId and (:type is null or c.type = :type)",
            countQuery = "select count(c) from Category c where c.userId = :userId and (:type is null or c.type = :type)")
    Page<Category> page(UUID userId, EntryType type, Pageable pageable);

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    Optional<Category> findByUserIdAndTypeAndNameIgnoreCase(UUID userId, EntryType type, String name);

    long countByUserIdAndType(UUID userId, EntryType type);
}
