package com.lifepedia.api.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommitmentMarkRepository extends JpaRepository<CommitmentMark, UUID> {

    List<CommitmentMark> findByCommitmentIdIn(Collection<UUID> commitmentIds);

    List<CommitmentMark> findByCommitmentIdInAndDateBetween(Collection<UUID> commitmentIds, LocalDate from, LocalDate to);

    Optional<CommitmentMark> findByCommitmentIdAndDate(UUID commitmentId, LocalDate date);
}
