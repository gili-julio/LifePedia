package com.lifepedia.api.finance.importing;

import com.lifepedia.api.finance.importing.ImportDraft.Origin;
import com.lifepedia.api.finance.importing.ImportDraft.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportDraftRepository extends JpaRepository<ImportDraft, UUID> {

    Optional<ImportDraft> findByIdAndUserId(UUID id, UUID userId);

    Optional<ImportDraft> findFirstByUserIdAndStatusIn(UUID userId, Collection<Status> statuses);

    List<ImportDraft> findByStatusAndCreatedAtBefore(Status status, OffsetDateTime before);

    List<ImportDraft> findByStatusAndUpdatedAtBefore(Status status, OffsetDateTime before);

    /** Datas dos envios que contam nos limites, do mais novo para o mais antigo. */
    @Query("""
            select d.createdAt from ImportDraft d
            where d.userId = :userId and d.origin = :origin and d.status <> :failed and d.createdAt > :since
            order by d.createdAt desc
            """)
    List<OffsetDateTime> sentSince(@Param("userId") UUID userId, @Param("origin") Origin origin,
                                   @Param("failed") Status failed, @Param("since") OffsetDateTime since);

    @Modifying
    @Query("delete from ImportDraft d where d.status = :status and d.updatedAt < :before")
    int deleteClosed(@Param("status") Status status, @Param("before") OffsetDateTime before);
}
