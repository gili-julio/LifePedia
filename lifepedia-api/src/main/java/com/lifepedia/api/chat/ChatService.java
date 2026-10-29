package com.lifepedia.api.chat;

import com.lifepedia.api.chat.ChatDtos.ChatEvent;
import com.lifepedia.api.chat.ChatDtos.EditRequest;
import com.lifepedia.api.chat.ChatDtos.MessagePage;
import com.lifepedia.api.chat.ChatDtos.MessageRequest;
import com.lifepedia.api.chat.ChatDtos.MessageResponse;
import com.lifepedia.api.chat.ChatDtos.ReplyPreview;
import com.lifepedia.api.chat.GroupMessageRepository.UnreadCount;
import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRemovedEvent;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.group.UserGroup;
import com.lifepedia.api.group.UserGroupRepository;
import com.lifepedia.api.notification.push.PushService;
import com.lifepedia.api.notification.push.PushTexts;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Chat do grupo: histórico paginado por cursor, enviar/editar/apagar a própria mensagem, leitura e não lidas. */
@Service
public class ChatService {

    static final int MESSAGES_PER_MINUTE = 30;
    private static final int DEFAULT_PAGE = 30;
    private static final int MAX_PAGE = 100;
    private static final int REPLY_PREVIEW = 140;

    private final GroupMessageRepository messages;
    private final GroupAccess access;
    private final GroupMemberRepository members;
    private final UserRepository users;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final UserGroupRepository groups;
    private final PushService push;

    public ChatService(GroupMessageRepository messages, GroupAccess access, GroupMemberRepository members,
                       UserRepository users, RateLimiter rateLimiter, ApplicationEventPublisher events,
                       UserGroupRepository groups, PushService push) {
        this.messages = messages;
        this.access = access;
        this.members = members;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.groups = groups;
        this.push = push;
    }

    /** @param before id da mensagem mais antiga já carregada; nulo = as mais recentes */
    @Transactional(readOnly = true)
    public MessagePage page(UUID userId, UUID groupId, UUID before, Integer size) {
        access.requireMember(groupId, userId);
        int limit = size == null ? DEFAULT_PAGE : Math.clamp(size, 1, MAX_PAGE);
        PageRequest page = PageRequest.of(0, limit + 1);
        List<GroupMessage> found = before == null ? messages.latest(groupId, page)
                : messages.findByIdAndGroupId(before, groupId)
                .map(b -> messages.before(groupId, b.getCreatedAt(), b.getId(), page))
                .orElseThrow(() -> new NotFoundException("Mensagem não encontrada."));
        boolean hasMore = found.size() > limit;
        List<GroupMessage> items = found.subList(0, Math.min(limit, found.size())).reversed();
        return new MessagePage(responses(items), hasMore);
    }

    @Transactional
    public MessageResponse send(UUID userId, UUID groupId, MessageRequest request) {
        access.requireMember(groupId, userId);
        if (!rateLimiter.tryAcquire("chat:" + userId, MESSAGES_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitas mensagens seguidas. Espere um pouco.");
        }
        if (request.replyToId() != null && messages.findByIdAndGroupId(request.replyToId(), groupId).isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mensagem respondida não encontrada.");
        }
        GroupMessage message = messages.save(new GroupMessage(groupId, userId, request.replyToId(), request.body().trim()));
        messages.markRead(groupId, userId, message.getCreatedAt());
        MessageResponse response = publish(message);
        pushToOthers(message, response);
        return response;
    }

    @Transactional
    public MessageResponse edit(UUID userId, UUID groupId, UUID id, EditRequest request) {
        GroupMessage message = own(userId, groupId, id);
        if (message.isDeleted()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Mensagem apagada não pode ser editada.");
        message.edit(request.body().trim());
        return publish(message);
    }

    @Transactional
    public void delete(UUID userId, UUID groupId, UUID id) {
        GroupMessage message = own(userId, groupId, id);
        if (message.isDeleted()) return;
        message.delete();
        publish(message);
    }

    @Transactional
    public void markRead(UUID userId, UUID groupId) {
        access.requireMember(groupId, userId);
        messages.markRead(groupId, userId, OffsetDateTime.now());
    }

    /** Não lidas por grupo (só grupos com alguma). */
    @Transactional(readOnly = true)
    public Map<UUID, Long> unread(UUID userId) {
        return messages.unread(userId).stream().collect(Collectors.toMap(UnreadCount::getGroupId, UnreadCount::getTotal));
    }

    @EventListener
    public void onMemberRemoved(GroupMemberRemovedEvent event) {
        messages.forgetRead(event.groupId(), event.userId());
    }

    private GroupMessage own(UUID userId, UUID groupId, UUID id) {
        access.requireMember(groupId, userId);
        GroupMessage message = messages.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new NotFoundException("Mensagem não encontrada."));
        if (!message.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Só quem escreveu a mensagem pode mudá-la.");
        }
        return message;
    }

    /** Monta a resposta e avisa os membros depois do commit ({@link ChatStreams}). */
    private MessageResponse publish(GroupMessage message) {
        MessageResponse response = responses(List.of(message)).getFirst();
        List<UUID> recipients = members.findByGroupIdOrderByCreatedAtAsc(message.getGroupId()).stream()
                .map(GroupMember::getUserId).toList();
        events.publishEvent(new ChatEvent(recipients, response));
        return response;
    }

    /** Push da mensagem nova para os outros membros (o app aberto não mostra, o service worker decide). */
    private void pushToOthers(GroupMessage message, MessageResponse response) {
        List<UUID> others = members.findByGroupIdOrderByCreatedAtAsc(message.getGroupId()).stream()
                .map(GroupMember::getUserId).filter(id -> !id.equals(message.getUserId())).toList();
        String groupName = groups.findById(message.getGroupId()).map(UserGroup::getName).orElse("Grupo");
        push.send(others, PushTexts.chat(message.getGroupId(), groupName, response.author().name(), message.getBody()));
    }

    private List<MessageResponse> responses(List<GroupMessage> list) {
        Set<UUID> replyIds = list.stream().map(GroupMessage::getReplyToId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, GroupMessage> replies = messages.findAllById(replyIds).stream()
                .collect(Collectors.toMap(GroupMessage::getId, Function.identity()));
        Set<UUID> userIds = new HashSet<>();
        list.forEach(m -> userIds.add(m.getUserId()));
        replies.values().forEach(m -> userIds.add(m.getUserId()));
        Map<UUID, UserSummary> authors = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, UserSummary::from));
        return list.stream().map(m -> {
            GroupMessage r = m.getReplyToId() == null ? null : replies.get(m.getReplyToId());
            ReplyPreview reply = r == null ? null : new ReplyPreview(r.getId(), authors.get(r.getUserId()), preview(r.getBody()));
            return new MessageResponse(m.getId(), m.getGroupId(), authors.get(m.getUserId()), m.getBody(), reply,
                    m.getCreatedAt(), m.getEditedAt(), m.isDeleted());
        }).toList();
    }

    private static String preview(String body) {
        if (body == null || body.length() <= REPLY_PREVIEW) return body;
        return body.substring(0, REPLY_PREVIEW) + "...";
    }
}
