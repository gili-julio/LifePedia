package com.lifepedia.api.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditEntry, UUID> {

    @Query(value = "select a from AuditEntry a", countQuery = "select count(a) from AuditEntry a")
    Page<AuditEntry> page(Pageable pageable);
}
