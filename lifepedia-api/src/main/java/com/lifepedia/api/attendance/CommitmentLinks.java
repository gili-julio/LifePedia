package com.lifepedia.api.attendance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** O que outros módulos (ex.: notas) leem dos compromissos da pessoa. */
@Service
public class CommitmentLinks {

    private final CommitmentRepository commitments;
    private final AttendanceService attendance;

    public CommitmentLinks(CommitmentRepository commitments, AttendanceService attendance) {
        this.commitments = commitments;
        this.attendance = attendance;
    }

    public record LinkedCommitment(UUID id, String name) {
    }

    /** Contagem do compromisso; {@code remaining} = faltas que ainda cabem (negativo = passou). */
    public record CommitmentCount(UUID id, String name, int presences, int absences, int remaining) {
    }

    @Transactional(readOnly = true)
    public Optional<LinkedCommitment> personal(UUID userId, UUID commitmentId) {
        return commitments.findByIdAndUserId(commitmentId, userId).map(CommitmentLinks::linked);
    }

    @Transactional(readOnly = true)
    public Map<UUID, LinkedCommitment> personal(UUID userId, Collection<UUID> ids) {
        return commitments.findAllById(ids).stream().filter(c -> userId.equals(c.getUserId()))
                .collect(Collectors.toMap(Commitment::getId, CommitmentLinks::linked));
    }

    /** Todos os compromissos da pessoa, por nome. */
    @Transactional(readOnly = true)
    public List<LinkedCommitment> all(UUID userId) {
        return commitments.findByUserIdOrderByNameAsc(userId).stream().map(CommitmentLinks::linked).toList();
    }

    /** Contagem de cada compromisso da pessoa, como na tela da Frequência. */
    @Transactional(readOnly = true)
    public Map<UUID, CommitmentCount> counts(UUID userId) {
        return attendance.list(userId).stream().collect(Collectors.toMap(c -> c.id(),
                c -> new CommitmentCount(c.id(), c.name(), c.stats().presences(), c.stats().absences(), c.stats().remaining())));
    }

    private static LinkedCommitment linked(Commitment commitment) {
        return new LinkedCommitment(commitment.getId(), commitment.getName());
    }
}
