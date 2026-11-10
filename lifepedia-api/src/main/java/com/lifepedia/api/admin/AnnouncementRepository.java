package com.lifepedia.api.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    @Query("select a from Announcement a where a.endedAt is null and (a.endsAt is null or a.endsAt > :now) "
            + "order by a.createdAt desc")
    List<Announcement> findActive(OffsetDateTime now);

    @Query(value = "select a from Announcement a", countQuery = "select count(a) from Announcement a")
    Page<Announcement> page(Pageable pageable);
}
