package com.lifepedia.api.admin;

import com.lifepedia.api.admin.AdminDtos.ActiveAnnouncement;
import com.lifepedia.api.admin.AdminDtos.AnnouncementRequest;
import com.lifepedia.api.admin.AdminDtos.AnnouncementResponse;
import com.lifepedia.api.admin.AuditEntry.Action;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.PageResponse;
import com.lifepedia.api.shared.Paging;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Avisos no topo do app: admins criam e encerram; todos veem os ativos. */
@Service
public class AnnouncementService {

    static final int MAX_ACTIVE = 3;
    static final int MAX_DAYS = 90;
    private static final Map<String, String> SORT_FIELDS = Map.of("createdAt", "a.createdAt");

    private final AnnouncementRepository repository;
    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;

    public AnnouncementService(AnnouncementRepository repository, UserRepository users, AuditService audit, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ActiveAnnouncement> active() {
        return repository.findActive(OffsetDateTime.now(clock)).stream().map(ActiveAnnouncement::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> page(int page, int size) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return PageResponse.of(repository.page(Paging.pageable(page, size, null, SORT_FIELDS, "createdAt,desc", "a.id")), items -> {
            Map<UUID, User> authors = users.findAllById(items.stream().map(Announcement::getCreatedBy).filter(Objects::nonNull)
                    .distinct().toList()).stream().collect(Collectors.toMap(User::getId, Function.identity()));
            return items.stream().map(a -> {
                User author = a.getCreatedBy() == null ? null : authors.get(a.getCreatedBy());
                return new AnnouncementResponse(a.getId(), a.getMessage(), a.getLevel(), a.getEndsAt(), a.getEndedAt(),
                        a.isActive(now), author == null ? null : author.getName(), a.getCreatedAt());
            }).toList();
        });
    }

    @Transactional
    public ActiveAnnouncement create(UUID actorId, AnnouncementRequest request) {
        User actor = users.findById(actorId).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (request.endsAt() != null && (!request.endsAt().isAfter(now) || request.endsAt().isAfter(now.plusDays(MAX_DAYS)))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O fim do aviso precisa ser no futuro, em até " + MAX_DAYS + " dias.");
        }
        if (repository.findActive(now).size() >= MAX_ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "Já há " + MAX_ACTIVE + " avisos no ar. Encerre um antes.");
        }
        Announcement saved = repository.save(new Announcement(request.message().trim(), request.level(), request.endsAt(), actorId));
        audit.record(actor, Action.ANNOUNCEMENT_CREATED, null, saved.getMessage());
        return ActiveAnnouncement.from(saved);
    }

    @Transactional
    public void end(UUID actorId, UUID id) {
        User actor = users.findById(actorId).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        Announcement announcement = repository.findById(id).orElseThrow(() -> new NotFoundException("Aviso não encontrado."));
        if (!announcement.isActive(OffsetDateTime.now(clock))) return;
        announcement.end();
        audit.record(actor, Action.ANNOUNCEMENT_ENDED, null, announcement.getMessage());
    }
}
