package com.lifepedia.api.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommitmentRepository extends JpaRepository<Commitment, UUID> {

    Optional<Commitment> findByIdAndUserId(UUID id, UUID userId);

    List<Commitment> findByUserIdOrderByNameAsc(UUID userId);

    long countByUserId(UUID userId);
}
