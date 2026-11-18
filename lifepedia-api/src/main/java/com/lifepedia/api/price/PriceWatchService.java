package com.lifepedia.api.price;

import com.lifepedia.api.config.PriceProperties;
import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupDeletedEvent;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.group.UserGroup;
import com.lifepedia.api.group.UserGroupRepository;
import com.lifepedia.api.planning.LinkMetadataParser;
import com.lifepedia.api.planning.PlanItemPrices;
import com.lifepedia.api.price.PriceDtos.CreateRequest;
import com.lifepedia.api.price.PriceDtos.HistoryPoint;
import com.lifepedia.api.price.PriceDtos.LinkView;
import com.lifepedia.api.price.PriceDtos.ManualData;
import com.lifepedia.api.price.PriceDtos.ManualPriceRequest;
import com.lifepedia.api.price.PriceDtos.ManualView;
import com.lifepedia.api.price.PriceDtos.Settings;
import com.lifepedia.api.price.PriceDtos.Stats;
import com.lifepedia.api.price.PriceDtos.UpdateRequest;
import com.lifepedia.api.price.PriceDtos.WatchData;
import com.lifepedia.api.price.PriceDtos.WatchDetail;
import com.lifepedia.api.price.PriceDtos.WatchSummary;
import com.lifepedia.api.price.PricePoint.Source;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.shared.image.ImageFile;
import com.lifepedia.api.shared.net.SafeHttpClient;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Itens com preço acompanhado: pessoais (só o dono) e dos grupos (membros veem e editam; excluir é do criador ou de admin
 * do grupo). Quem não vê recebe 404.
 */
@Service
public class PriceWatchService {

    static final int MAX_LINKS = 5;
    private static final int MAX_MANUAL = 200;
    private static final int REFRESH_PER_HOUR = 20;
    private static final Duration REFRESH = Duration.ofMinutes(15);
    private static final int DEFAULT_DROP = 10;

    private final PriceWatchRepository watches;
    private final PriceLinkRepository links;
    private final PricePointRepository points;
    private final PriceCheckService checks;
    private final PriceImageStore images;
    private final PlanItemPrices plans;
    private final GroupAccess groupAccess;
    private final GroupMemberRepository members;
    private final UserGroupRepository groups;
    private final UserRepository users;
    private final RateLimiter rateLimiter;
    private final PriceProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PriceWatchService(PriceWatchRepository watches, PriceLinkRepository links, PricePointRepository points,
                             PriceCheckService checks, PriceImageStore images, PlanItemPrices plans, GroupAccess groupAccess,
                             GroupMemberRepository members, UserGroupRepository groups, UserRepository users,
                             RateLimiter rateLimiter, PriceProperties properties, PlatformTransactionManager transactions,
                             Clock clock) {
        this.watches = watches;
        this.links = links;
        this.points = points;
        this.checks = checks;
        this.images = images;
        this.plans = plans;
        this.groupAccess = groupAccess;
        this.members = members;
        this.groups = groups;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** Sem grupo: os pessoais e os de todos os grupos da pessoa; com grupo: só os dele (membro). */
    @Transactional(readOnly = true)
    public List<WatchSummary> list(UUID userId, UUID groupId) {
        List<PriceWatch> items = new ArrayList<>();
        if (groupId != null) {
            groupAccess.requireMember(groupId, userId);
            items.addAll(watches.findByGroupIdInOrderByCreatedAtDesc(List.of(groupId)));
        } else {
            items.addAll(watches.findByUserIdAndGroupIdIsNullOrderByCreatedAtDesc(userId));
            List<UUID> mine = members.findByUserId(userId).stream().map(GroupMember::getGroupId).toList();
            if (!mine.isEmpty()) items.addAll(watches.findByGroupIdInOrderByCreatedAtDesc(mine));
        }
        return summaries(userId, items);
    }

    @Transactional(readOnly = true)
    public WatchDetail get(UUID userId, UUID id) {
        PriceWatch w = visible(userId, id);
        LocalDate today = LocalDate.now(clock);
        List<HistoryPoint> history = PriceBook.daily(points.findByWatchIdAndDayGreaterThanEqualOrderByDayAsc(id,
                today.minusDays(PriceBook.HISTORY_DAYS)));
        Stats stats = PriceBook.stats(history, w.getBestPrice(), today);
        List<LinkView> linkViews = links.findByWatchIdOrderByCreatedAtAsc(id).stream()
                .map(l -> new LinkView(l.getId(), l.getUrl(), l.getStore(), l.getLastPrice(), l.getLastCheckedAt(), l.getLastError()))
                .toList();
        List<ManualView> manual = points.findByWatchIdAndSourceOrderByDayDesc(id, Source.MANUAL).stream()
                .map(p -> new ManualView(p.getId(), p.getDay(), p.getStore(), p.getPrice(), p.getUrl())).toList();
        return new WatchDetail(summaries(userId, List.of(w)).getFirst(), w.getBuscapeUrl(), checks.offers(w), history, stats,
                linkViews, manual);
    }

    /** Grava e já consulta (fora da transação). */
    public WatchDetail create(UUID userId, CreateRequest request) {
        List<String> urls = request.links() == null ? List.of() : request.links().stream().map(PriceWatchService::url).distinct().toList();
        UUID id = transaction.execute(status -> {
            if (watches.countByUserId(userId) >= properties.maxWatches()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Limite de " + properties.maxWatches() + " itens acompanhados. Exclua os que não precisa mais.");
            }
            if (request.groupId() != null) groupAccess.requireMember(request.groupId(), userId);
            PriceWatch w = new PriceWatch(userId, request.groupId());
            if (request.planItemId() != null) {
                if (request.groupId() == null || plans.find(request.planItemId(), request.groupId()).isEmpty()) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "Item do planejamento inválido.");
                }
                if (watches.findByPlanItemId(request.planItemId()).isPresent()) {
                    throw new BusinessException(HttpStatus.CONFLICT, "Esse item do planejamento já tem o preço acompanhado.");
                }
                w.setPlanItemId(request.planItemId());
            }
            apply(w, request.name(), request.settings() == null ? Settings.DEFAULT : request.settings());
            watches.save(w);
            for (String url : urls) links.save(new PriceLink(w.getId(), url, site(url), userId));
            if (request.price() != null) addManual(w, request.price(), userId);
            return w.getId();
        });
        checks.check(List.of(id));
        return transaction.execute(status -> get(userId, id));
    }

    @Transactional
    public WatchDetail update(UUID userId, UUID id, UpdateRequest request) {
        PriceWatch w = visible(userId, id);
        apply(w, request.name(), request.settings());
        if (w.isUpdatePlan()) checks.refreshOffers(w);
        return get(userId, id);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        PriceWatch w = visible(userId, id);
        if (!canDelete(userId, w)) throw new BusinessException(HttpStatus.FORBIDDEN, "Só quem criou ou um admin do grupo pode excluir.");
        images.deleteAfterCommit(w.getImageKey());
        watches.delete(w);
    }

    /** Atualizar agora: no máximo a cada 15 min por item, 20 por hora por pessoa e dentro da cota do dia. */
    public WatchDetail refresh(UUID userId, UUID id) {
        PriceWatch w = transaction.execute(status -> visible(userId, id));
        OffsetDateTime last = w.getLastCheckedAt();
        if (last != null && last.plus(REFRESH).isAfter(OffsetDateTime.now(clock))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Consultado há pouco. Tente de novo em alguns minutos.");
        }
        acquire(userId);
        checks.check(List.of(id));
        return transaction.execute(status -> get(userId, id));
    }

    /** Link de loja novo, já consultado. */
    public WatchDetail addLink(UUID userId, UUID id, String rawUrl) {
        String url = url(rawUrl);
        transaction.executeWithoutResult(status -> {
            PriceWatch w = visible(userId, id);
            if (links.existsByWatchIdAndUrl(w.getId(), url)) throw new BusinessException(HttpStatus.CONFLICT, "Esse link já está no item.");
            if (links.countByWatchId(w.getId()) >= MAX_LINKS) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "No máximo " + MAX_LINKS + " links por item.");
            }
            links.save(new PriceLink(w.getId(), url, site(url), userId));
        });
        acquire(userId);
        checks.check(List.of(id));
        return transaction.execute(status -> get(userId, id));
    }

    @Transactional
    public WatchDetail removeLink(UUID userId, UUID id, UUID linkId) {
        visible(userId, id);
        links.delete(links.findByIdAndWatchId(linkId, id).orElseThrow(() -> new NotFoundException("Link não encontrado.")));
        return get(userId, id);
    }

    /** Preço visto pela pessoa (na loja, no app ou pelo botão do navegador); vale como oferta por alguns dias. */
    @Transactional
    public WatchDetail addPrice(UUID userId, UUID id, ManualPriceRequest request) {
        PriceWatch w = visible(userId, id);
        addManual(w, request, userId);
        checks.refreshOffers(w);
        return get(userId, id);
    }

    @Transactional
    public WatchDetail removePrice(UUID userId, UUID id, UUID pointId) {
        PriceWatch w = visible(userId, id);
        PricePoint point = points.findByIdAndWatchId(pointId, id).filter(p -> p.getSource() == Source.MANUAL)
                .orElseThrow(() -> new NotFoundException("Preço não encontrado."));
        points.delete(point);
        points.flush();
        checks.refreshOffers(w);
        return get(userId, id);
    }

    @Transactional(readOnly = true)
    public ImageFile image(UUID userId, UUID id) {
        PriceWatch w = visible(userId, id);
        if (w.getImageKey() == null) throw new NotFoundException("Sem foto.");
        return images.file(w.getImageKey());
    }

    @Transactional(readOnly = true)
    public List<WatchData> exportData(UUID userId) {
        List<PriceWatch> own = watches.findByUserIdOrderByCreatedAtAsc(userId);
        Map<UUID, String> groupNames = groups.findAllById(own.stream().map(PriceWatch::getGroupId).filter(Objects::nonNull)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(UserGroup::getId, UserGroup::getName));
        return own.stream().map(w -> new WatchData(w.getName(), groupNames.get(w.getGroupId()), w.getTargetPrice(),
                w.getDropPercent(), w.isActive(), w.getBestPrice(), w.getBestStore(),
                links.findByWatchIdOrderByCreatedAtAsc(w.getId()).stream().map(PriceLink::getUrl).toList(),
                points.findByWatchIdAndSourceOrderByDayDesc(w.getId(), Source.MANUAL).stream()
                        .map(p -> new ManualData(p.getDay(), p.getStore(), p.getPrice())).toList(),
                w.getCreatedAt())).toList();
    }

    /** Conta excluída: as fotos saem do disco (o banco apaga as linhas em cascata). */
    public void deleteUserImagesAfterCommit(UUID userId) {
        watches.imageKeysOf(userId).forEach(images::deleteAfterCommit);
    }

    @EventListener
    public void onGroupDeleted(GroupDeletedEvent event) {
        watches.imageKeysOfGroup(event.groupId()).forEach(images::deleteAfterCommit);
    }

    private List<WatchSummary> summaries(UUID userId, List<PriceWatch> items) {
        if (items.isEmpty()) return List.of();
        Set<UUID> groupIds = items.stream().map(PriceWatch::getGroupId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, UserGroup> groupById = groups.findAllById(groupIds).stream()
                .collect(Collectors.toMap(UserGroup::getId, Function.identity()));
        Map<UUID, User> ownerById = users.findAllById(items.stream().map(PriceWatch::getUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<UUID> adminOf = members.findByUserId(userId).stream().filter(GroupMember::isAdmin).map(GroupMember::getGroupId)
                .collect(Collectors.toSet());
        LocalDate today = LocalDate.now(clock);
        return items.stream().map(w -> {
            Stats stats = PriceBook.stats(PriceBook.daily(points.findByWatchIdAndDayGreaterThanEqualOrderByDayAsc(w.getId(),
                    today.minusDays(31))), w.getBestPrice(), today);
            UserGroup group = groupById.get(w.getGroupId());
            User owner = ownerById.get(w.getUserId());
            boolean mine = w.getUserId().equals(userId);
            boolean atTarget = w.getTargetPrice() != null && w.getBestPrice() != null
                    && w.getBestPrice().compareTo(w.getTargetPrice()) <= 0;
            return new WatchSummary(w.getId(), w.getName(), w.getGroupId(), group == null ? null : group.getName(),
                    w.getPlanItemId(), w.imageUrl(), w.getBestPrice(), w.getBestStore(), w.getBestUrl(), w.getStoreCount(),
                    w.getTargetPrice(), w.getDropPercent(), w.isNotifyGroup(), w.isUpdatePlan(), w.isActive(),
                    stats.change7(), stats.change30(), atTarget, w.getLastCheckedAt(), w.getLastError(),
                    owner == null ? null : UserSummary.from(owner), mine, mine || adminOf.contains(w.getGroupId()),
                    w.getCreatedAt());
        }).toList();
    }

    private void addManual(PriceWatch w, ManualPriceRequest request, UUID userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate day = request.day() == null ? today : request.day();
        if (day.isAfter(today)) throw new BusinessException(HttpStatus.BAD_REQUEST, "A data não pode ser no futuro.");
        if (points.countByWatchIdAndSource(w.getId(), Source.MANUAL) >= MAX_MANUAL) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_MANUAL + " preços anotados no item.");
        }
        String store = request.store().trim();
        String link = request.url() == null || request.url().isBlank() ? null : url(request.url());
        PricePoint point = points.findByWatchIdAndDayAndStoreAndSource(w.getId(), day, store, Source.MANUAL)
                .orElseGet(() -> new PricePoint(w.getId(), day, store, Source.MANUAL, userId));
        point.setPrice(request.price(), link);
        points.save(point);
        points.flush();
    }

    private void apply(PriceWatch w, String name, Settings s) {
        boolean group = w.getGroupId() != null;
        w.set(name.trim(), s.targetPrice(), s.dropPercent() == null ? DEFAULT_DROP : s.dropPercent(),
                group && Boolean.TRUE.equals(s.notifyGroup()), w.getPlanItemId() != null && Boolean.TRUE.equals(s.updatePlan()),
                s.active() == null || s.active());
    }

    private void acquire(UUID userId) {
        if (!rateLimiter.tryAcquire("price-refresh:" + userId, REFRESH_PER_HOUR, Duration.ofHours(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitas consultas de preço. Tente mais tarde.");
        }
        if (!checks.underDailyLimit()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "As consultas de preço de hoje acabaram. Amanhã volta.");
        }
    }

    private PriceWatch visible(UUID userId, UUID id) {
        PriceWatch w = watches.findById(id).orElseThrow(() -> new NotFoundException("Item não encontrado."));
        if (w.getGroupId() == null ? w.getUserId().equals(userId) : members.existsByGroupIdAndUserId(w.getGroupId(), userId)) {
            return w;
        }
        throw new NotFoundException("Item não encontrado.");
    }

    private boolean canDelete(UUID userId, PriceWatch w) {
        if (w.getUserId().equals(userId)) return true;
        return w.getGroupId() != null && members.findByGroupIdAndUserId(w.getGroupId(), userId).map(GroupMember::isAdmin).orElse(false);
    }

    private static String url(String raw) {
        try {
            URI uri = SafeHttpClient.parseUserUrl(raw.trim());
            return uri.toString();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Link inválido. Use um endereço http(s) completo.");
        }
    }

    private static String site(String url) {
        String site = LinkMetadataParser.siteName(URI.create(url));
        return site == null ? null : site.length() > 80 ? site.substring(0, 80) : site;
    }
}
