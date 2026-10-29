package com.lifepedia.api.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, UUID> {

    Optional<GroupMessage> findByIdAndGroupId(UUID id, UUID groupId);

    /** Mais novas primeiro. */
    @Query("select m from GroupMessage m where m.groupId = :groupId order by m.createdAt desc, m.id desc")
    List<GroupMessage> latest(UUID groupId, Pageable pageable);

    /** Mais novas primeiro, antes de {@code before} (cursor: data + id de desempate). */
    @Query("select m from GroupMessage m where m.groupId = :groupId and (m.createdAt < :createdAt "
            + "or (m.createdAt = :createdAt and m.id < :id)) order by m.createdAt desc, m.id desc")
    List<GroupMessage> before(UUID groupId, OffsetDateTime createdAt, UUID id, Pageable pageable);

    @Modifying
    @Query(value = "insert into group_chat_reads (group_id, user_id, read_at) values (:groupId, :userId, :readAt) "
            + "on conflict (group_id, user_id) do update set read_at = greatest(group_chat_reads.read_at, excluded.read_at)",
            nativeQuery = true)
    void markRead(UUID groupId, UUID userId, OffsetDateTime readAt);

    @Modifying
    @Query(value = "delete from group_chat_reads where group_id = :groupId and user_id = :userId", nativeQuery = true)
    void forgetRead(UUID groupId, UUID userId);

    /** Não lidas por grupo: dos outros, não apagadas, depois da última leitura (ou de quando a pessoa entrou). */
    @Query(value = """
            select m.group_id as groupId, count(*) as total
            from group_messages m
            join group_members gm on gm.group_id = m.group_id and gm.user_id = :userId
            left join group_chat_reads r on r.group_id = m.group_id and r.user_id = :userId
            where m.user_id <> :userId and m.deleted_at is null and m.created_at > coalesce(r.read_at, gm.created_at)
            group by m.group_id
            """, nativeQuery = true)
    List<UnreadCount> unread(UUID userId);

    interface UnreadCount {
        UUID getGroupId();

        long getTotal();
    }
}
