package com.lifepedia.api.friend;

import com.lifepedia.api.achievement.Achievement;
import com.lifepedia.api.achievement.AchievementProgress;
import com.lifepedia.api.achievement.AchievementService;
import com.lifepedia.api.friend.FriendDtos.Candidate;
import com.lifepedia.api.friend.FriendDtos.FriendProfile;
import com.lifepedia.api.friend.FriendDtos.FriendRequestResult;
import com.lifepedia.api.friend.FriendDtos.FriendResponse;
import com.lifepedia.api.friend.FriendDtos.RequestResponse;
import com.lifepedia.api.friend.FriendDtos.Requests;
import com.lifepedia.api.friend.Friendship.Status;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import com.lifepedia.api.user.Usernames;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Pedidos de amizade (enviar, aceitar, recusar/cancelar), lista de amigos e perfil de um amigo. */
@Service
public class FriendService {

    static final int MAX_FRIENDS = 200;
    static final int MAX_PENDING_SENT = 50;
    static final int REQUESTS_PER_HOUR = 30;
    private static final int MIN_SEARCH = 2;
    private static final Collator PT_BR = Collator.getInstance(Locale.of("pt", "BR"));

    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AchievementService achievements;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;

    public FriendService(FriendshipRepository friendships, UserRepository users, NotificationService notifications,
                         AchievementService achievements, RateLimiter rateLimiter, ApplicationEventPublisher events) {
        this.friendships = friendships;
        this.users = users;
        this.notifications = notifications;
        this.achievements = achievements;
        this.rateLimiter = rateLimiter;
        this.events = events;
    }

    /** Amigos por nome. */
    @Transactional(readOnly = true)
    public List<FriendResponse> list(UUID userId) {
        List<Friendship> all = friendships.involving(userId, Status.ACCEPTED);
        Map<UUID, User> byId = usersOf(all.stream().map(f -> f.other(userId)).toList());
        return all.stream()
                .map(f -> new FriendResponse(UserSummary.from(byId.get(f.other(userId))), f.getAcceptedAt()))
                .sorted(Comparator.comparing((FriendResponse f) -> f.user().name(), PT_BR))
                .toList();
    }

    @Transactional(readOnly = true)
    public Requests requests(UUID userId) {
        List<Friendship> received = friendships.findByAddresseeIdAndStatusOrderByCreatedAtDesc(userId, Status.PENDING);
        List<Friendship> sent = friendships.findByRequesterIdAndStatusOrderByCreatedAtDesc(userId, Status.PENDING);
        Map<UUID, User> byId = usersOf(Stream.concat(received.stream(), sent.stream())
                .map(f -> f.other(userId)).toList());
        Function<Friendship, RequestResponse> map = f ->
                new RequestResponse(f.getId(), UserSummary.from(byId.get(f.other(userId))), f.getCreatedAt());
        return new Requests(received.stream().map(map).toList(), sent.stream().map(map).toList());
    }

    @Transactional(readOnly = true)
    public long receivedCount(UUID userId) {
        return friendships.countByAddresseeIdAndStatus(userId, Status.PENDING);
    }

    /** Busca por nome ou @username (mín. 2 caracteres, até 10), marcando amigos e pedidos pendentes. */
    @Transactional(readOnly = true)
    public List<Candidate> candidates(UUID userId, String query) {
        String term = Usernames.normalize(query);
        if (term.length() < MIN_SEARCH) return List.of();
        List<User> found = users.search(term, userId, PageRequest.of(0, 10));
        if (found.isEmpty()) return List.of();
        Map<UUID, Friendship> links = friendships.withAny(userId, found.stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(f -> f.other(userId), Function.identity()));
        return found.stream().map(u -> new Candidate(u.getId(), u.getName(), u.getUsername(), u.imageUrl(),
                status(links.get(u.getId()), userId))).toList();
    }

    /** Envia o pedido; se a pessoa já tinha pedido, aceita o dela. */
    @Transactional
    public FriendRequestResult request(UUID userId, UUID targetId) {
        if (userId.equals(targetId)) throw new BusinessException(HttpStatus.BAD_REQUEST, "Você não pode adicionar a si mesmo.");
        User target = users.findById(targetId).filter(u -> u.getUsername() != null)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        Friendship existing = friendships.between(userId, targetId).orElse(null);
        if (existing != null) {
            if (!existing.isPending()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Vocês já são amigos.");
            if (existing.getRequesterId().equals(userId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Pedido já enviado.");
            }
            acceptRequest(existing);
            return new FriendRequestResult(Status.ACCEPTED);
        }
        if (!rateLimiter.tryAcquire("friend-request:" + userId, REQUESTS_PER_HOUR, Duration.ofHours(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitos pedidos seguidos. Tente de novo mais tarde.");
        }
        if (friendships.countByRequesterIdAndStatus(userId, Status.PENDING) >= MAX_PENDING_SENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Você tem muitos pedidos sem resposta. Cancele alguns antes de enviar outros.");
        }
        requireRoom(userId);
        Friendship request = friendships.save(new Friendship(userId, target.getId()));
        notifications.notify(target.getId(), NotificationType.FRIEND_REQUEST, userId, null,
                actorData(userId, Map.of("requestId", request.getId().toString())));
        return new FriendRequestResult(Status.PENDING);
    }

    @Transactional
    public void accept(UUID userId, UUID requestId) {
        Friendship request = friendships.findById(requestId)
                .filter(f -> f.isPending() && f.getAddresseeId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Pedido não encontrado ou já respondido."));
        acceptRequest(request);
    }

    /** Recusa (quem recebeu) ou cancela (quem enviou) um pedido pendente. */
    @Transactional
    public void dismiss(UUID userId, UUID requestId) {
        friendships.delete(friendships.findById(requestId)
                .filter(f -> f.isPending() && f.involves(userId))
                .orElseThrow(() -> new NotFoundException("Pedido não encontrado ou já respondido.")));
    }

    @Transactional
    public void remove(UUID userId, UUID friendId) {
        friendships.delete(friendship(userId, friendId));
    }

    /** Perfil de um amigo com as conquistas que ele mostra (pode gravar nível novo dele). */
    @Transactional
    public FriendProfile profile(UUID userId, UUID friendId) {
        Friendship friendship = friendship(userId, friendId);
        User friend = users.findById(friendId).orElseThrow(() -> new NotFoundException("Amigo não encontrado."));
        return new FriendProfile(UserSummary.from(friend), friendship.getAcceptedAt(), achievements.of(friendId, false));
    }

    private Friendship friendship(UUID userId, UUID friendId) {
        return friendships.between(userId, friendId).filter(f -> !f.isPending())
                .orElseThrow(() -> new NotFoundException("Amigo não encontrado."));
    }

    private void acceptRequest(Friendship request) {
        requireRoom(request.getRequesterId());
        requireRoom(request.getAddresseeId());
        request.accept();
        events.publishEvent(new AchievementProgress(request.getRequesterId(), EnumSet.of(Achievement.FRIENDS)));
        events.publishEvent(new AchievementProgress(request.getAddresseeId(), EnumSet.of(Achievement.FRIENDS)));
        notifications.notify(request.getRequesterId(), NotificationType.FRIEND_ACCEPTED, request.getAddresseeId(), null,
                actorData(request.getAddresseeId(), Map.of()));
    }

    private void requireRoom(UUID userId) {
        if (friendships.countInvolving(userId, Status.ACCEPTED) >= MAX_FRIENDS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_FRIENDS + " amigos atingido.");
        }
    }

    private Map<String, String> actorData(UUID actorId, Map<String, String> extra) {
        User actor = users.findById(actorId).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        Map<String, String> data = new HashMap<>(extra);
        data.put("actorName", actor.getName());
        data.put("actorUsername", actor.getUsername() == null ? "" : actor.getUsername());
        return data;
    }

    private Map<UUID, User> usersOf(List<UUID> ids) {
        return users.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static String status(Friendship link, UUID userId) {
        if (link == null) return null;
        if (!link.isPending()) return "FRIEND";
        return link.getRequesterId().equals(userId) ? "SENT" : "RECEIVED";
    }
}
