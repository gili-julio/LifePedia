package com.lifepedia.api.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByGoogleSub(String googleSub);

    Optional<User> findByUsername(String username);

    /** Por nome (contém) ou username (começa com); só contas com username. @param term minúsculo */
    @Query("select u from User u where u.id <> :excludeId and u.username is not null "
            + "and (lower(u.name) like concat('%', :term, '%') or u.username like concat(:term, '%')) "
            + "order by lower(u.name), u.username")
    List<User> search(String term, UUID excludeId, Pageable pageable);

    @Query("select count(u) > 0 from User u where u.id = :id and u.admin = true and u.blockedAt is null")
    boolean isActiveAdmin(UUID id);

    @Query("select u.id from User u where u.blockedAt is null and (:onlyAdmins = false or u.admin = true)")
    List<UUID> findActiveIds(boolean onlyAdmins);

    /**
     * Lista do painel. @param term minúsculo, com {@code %} e {@code _} escapados por {@code !}; vazio = todos
     * @param filter {@code ALL}, {@code ADMIN} ou {@code BLOCKED}
     */
    @Query(value = "select u from User u where (:term = '' or lower(u.name) like concat('%', :term, '%') escape '!' "
            + "or u.email like concat('%', :term, '%') escape '!' or u.username like concat('%', :term, '%') escape '!') "
            + "and (:filter = 'ALL' or (:filter = 'ADMIN' and u.admin = true) or (:filter = 'BLOCKED' and u.blockedAt is not null))",
            countQuery = "select count(u) from User u where (:term = '' or lower(u.name) like concat('%', :term, '%') escape '!' "
                    + "or u.email like concat('%', :term, '%') escape '!' or u.username like concat('%', :term, '%') escape '!') "
                    + "and (:filter = 'ALL' or (:filter = 'ADMIN' and u.admin = true) "
                    + "or (:filter = 'BLOCKED' and u.blockedAt is not null))")
    Page<User> adminPage(String term, String filter, Pageable pageable);

    /** Nativo: {@code last_seen_at} fica fora das gravações da entidade (não volta valor antigo). */
    @Transactional
    @Modifying
    @Query(value = "update users set last_seen_at = :now where id = :id", nativeQuery = true)
    int touchLastSeen(UUID id, OffsetDateTime now);
}
