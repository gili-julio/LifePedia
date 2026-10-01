package com.lifepedia.api.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    /** Por nome (contém) ou username (começa com); só contas com username. @param term minúsculo */
    @Query("select u from User u where u.id <> :excludeId and u.username is not null "
            + "and (lower(u.name) like concat('%', :term, '%') or u.username like concat(:term, '%')) "
            + "order by lower(u.name), u.username")
    List<User> search(String term, UUID excludeId, Pageable pageable);
}
