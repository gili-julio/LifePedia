package com.lifepedia.api.note;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    /** Trava a linha até o fim da transação (duas gravações ao mesmo tempo não passam pela mesma versão). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Note n where n.id = :id")
    Optional<Note> lockById(UUID id);

    /** Filtros nulos não filtram; {@code q} já vem em minúsculas com {@code %} e escape. Fixadas primeiro. */
    @Query(value = """
            select n from Note n where (n.userId = :userId or n.groupId = :groupId)
              and (:template is null or n.template = :template)
              and ((:trashed = true and n.deletedAt is not null) or (:trashed = false and n.deletedAt is null))
              and (:archived is null or (:archived = true and n.archivedAt is not null)
                   or (:archived = false and n.archivedAt is null))
              and (:folderId is null or n.folderId = :folderId)
              and (:tagId is null or :tagId member of n.tagIds)
              and (:taskId is null or n.taskId = :taskId)
              and (:commitmentId is null or n.commitmentId = :commitmentId)
              and (:q is null or lower(n.title) like :q escape '!' or lower(n.content) like :q escape '!')
            order by case when n.pinnedAt is null then 1 else 0 end
            """, countQuery = """
            select count(n) from Note n where (n.userId = :userId or n.groupId = :groupId)
              and (:template is null or n.template = :template)
              and ((:trashed = true and n.deletedAt is not null) or (:trashed = false and n.deletedAt is null))
              and (:archived is null or (:archived = true and n.archivedAt is not null)
                   or (:archived = false and n.archivedAt is null))
              and (:folderId is null or n.folderId = :folderId)
              and (:tagId is null or :tagId member of n.tagIds)
              and (:taskId is null or n.taskId = :taskId)
              and (:commitmentId is null or n.commitmentId = :commitmentId)
              and (:q is null or lower(n.title) like :q escape '!' or lower(n.content) like :q escape '!')
            """)
    Page<Note> search(UUID userId, UUID groupId, Boolean template, boolean trashed, Boolean archived, UUID folderId,
                      UUID tagId, UUID taskId, UUID commitmentId, String q, Pageable pageable);

    /** Id, título e ícone das notas em uso (sem modelos nem lixeira), por título. */
    @Query("""
            select n.id, n.title, n.icon from Note n where (n.userId = :userId or n.groupId = :groupId)
              and n.deletedAt is null and n.template = false order by lower(n.title), n.id
            """)
    List<Object[]> refs(UUID userId, UUID groupId);

    /** Notas em uso que citam {@code pattern} (minúsculas, com {@code %} e escape {@code !}). */
    @Query("""
            select n.id, n.title, n.icon from Note n where (n.userId = :userId or n.groupId = :groupId)
              and n.deletedAt is null and n.template = false and n.id <> :exceptId
              and lower(n.content) like :pattern escape '!' order by lower(n.title), n.id
            """)
    List<Object[]> citing(UUID userId, UUID groupId, UUID exceptId, String pattern);

    @Query("select count(n) from Note n where n.userId = :userId or n.groupId = :groupId")
    long countOwned(UUID userId, UUID groupId);

    @Query("""
            select n.folderId, count(n) from Note n where n.folderId in :ids
              and n.deletedAt is null and n.archivedAt is null and n.template = false group by n.folderId
            """)
    List<Object[]> countByFolder(Collection<UUID> ids);

    @Query("""
            select t, count(n) from Note n join n.tagIds t where t in :ids
              and n.deletedAt is null and n.archivedAt is null and n.template = false group by t
            """)
    List<Object[]> countByTag(Collection<UUID> ids);

    List<Note> findByDeletedAtBefore(OffsetDateTime limit);

    List<Note> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
