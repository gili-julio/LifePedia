package com.lifepedia.api.notification;

import com.lifepedia.api.group.UserGroup;
import com.lifepedia.api.group.UserGroupRepository;
import com.lifepedia.api.notification.push.PushService;
import com.lifepedia.api.notification.push.PushTexts;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final TypeReference<Map<String, String>> DATA = new TypeReference<>() {
    };

    private final NotificationRepository repository;
    private final UserGroupRepository groups;
    private final JsonMapper json;
    private final PushService push;

    public NotificationService(NotificationRepository repository, UserGroupRepository groups, JsonMapper json, PushService push) {
        this.repository = repository;
        this.groups = groups;
        this.json = json;
        this.push = push;
    }

    /** Cria uma notificação para {@code userId} e manda o push depois do commit. @param data parâmetros do texto (só strings) */
    @Transactional
    public void notify(UUID userId, NotificationType type, UUID actorId, UUID groupId, Map<String, String> data) {
        repository.save(new Notification(userId, type, actorId, groupId, json.writeValueAsString(data)));
        push.send(List.of(userId), PushTexts.of(type, data, groupId));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> page(UUID userId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Paginação inválida.");
        }
        return PageResponse.of(repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)), items -> {
            Map<UUID, UserGroup> byId = groups.findAllById(items.stream().map(Notification::getGroupId)
                            .filter(Objects::nonNull).distinct().toList()).stream()
                    .collect(Collectors.toMap(UserGroup::getId, Function.identity()));
            return items.stream().map(n -> {
                UserGroup group = n.getGroupId() == null ? null : byId.get(n.getGroupId());
                return new NotificationResponse(n.getId(), n.getType(), json.readValue(n.getData(), DATA),
                        n.getGroupId(), group == null ? null : group.imageUrl(), n.getReadAt() != null, n.getCreatedAt());
            }).toList();
        });
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID id) {
        repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Notificação não encontrada."))
                .markRead();
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId, OffsetDateTime.now());
    }

    /** @param imageUrl imagem do grupo, se houver */
    public record NotificationResponse(UUID id, NotificationType type, Map<String, String> data, UUID groupId,
                                       String imageUrl, boolean read, OffsetDateTime createdAt) {
    }
}
