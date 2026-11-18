package com.lifepedia.api.price;

import com.lifepedia.api.config.PriceProperties;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.planning.LinkPreviewService;
import com.lifepedia.api.planning.LinkPreviewService.StorePrice;
import com.lifepedia.api.planning.PlanItemPrices;
import com.lifepedia.api.planning.PriceReferenceService;
import com.lifepedia.api.price.PriceBook.Alert;
import com.lifepedia.api.price.PriceDtos.Offer;
import com.lifepedia.api.price.PricePoint.Source;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.net.SafeHttpClient;
import com.lifepedia.api.shared.net.SafeHttpClient.FetchException;
import com.lifepedia.api.shared.net.SafeHttpClient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consulta de preço, sempre fora de transação: acha o produto no Buscapé (pelo nome, uma vez), lê as lojas e o histórico
 * da página dele e os links colados, grava um preço por loja e dia, recalcula o menor preço, avisa e atualiza o plano.
 */
@Service
public class PriceCheckService {

    static final Duration DUE = Duration.ofHours(20);
    private static final int MAX_PER_RUN = 200;
    private static final long PAUSE_MS = 1500;
    private static final int MAX_PAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 3 * 1024 * 1024;
    private static final TypeReference<List<Offer>> OFFERS = new TypeReference<>() {
    };
    private static final Logger log = LoggerFactory.getLogger(PriceCheckService.class);

    private record Target(String name, String buscapeUrl, boolean needsImage, Map<UUID, String> links) {
    }

    private record Fetched(String buscapeUrl, BuscapeProduct product, boolean buscapeFailed, Map<UUID, StorePrice> links,
                           byte[] image) {
    }

    private final PriceWatchRepository watches;
    private final PriceLinkRepository links;
    private final PricePointRepository points;
    private final PriceReferenceService references;
    private final LinkPreviewService stores;
    private final SafeHttpClient http;
    private final PriceImageStore images;
    private final PlanItemPrices plans;
    private final GroupMemberRepository members;
    private final NotificationService notifications;
    private final PriceProperties properties;
    private final TransactionTemplate transaction;
    private final JsonMapper json;
    private final Clock clock;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    public PriceCheckService(PriceWatchRepository watches, PriceLinkRepository links, PricePointRepository points,
                             PriceReferenceService references, LinkPreviewService stores, SafeHttpClient http,
                             PriceImageStore images, PlanItemPrices plans, GroupMemberRepository members,
                             NotificationService notifications, PriceProperties properties,
                             PlatformTransactionManager transactions, JsonMapper json, Clock clock) {
        this.watches = watches;
        this.links = links;
        this.points = points;
        this.references = references;
        this.stores = stores;
        this.http = http;
        this.images = images;
        this.plans = plans;
        this.members = members;
        this.notifications = notifications;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactions);
        this.json = json;
        this.clock = clock;
    }

    /** Job: os ativos sem consulta há {@link #DUE}, até a cota do dia. */
    public void checkDue() {
        long room = properties.dailyChecks() - checkedToday();
        if (room <= 0) return;
        OffsetDateTime before = OffsetDateTime.now(clock).minus(DUE);
        check(watches.due(before, PageRequest.of(0, (int) Math.min(room, MAX_PER_RUN))));
    }

    /** Consultas feitas hoje (para a cota e o painel do admin). */
    public long checkedToday() {
        return watches.countByLastCheckedAtAfter(LocalDate.now(clock).atStartOfDay(clock.getZone()).toOffsetDateTime());
    }

    public boolean underDailyLimit() {
        return checkedToday() < properties.dailyChecks();
    }

    /** Itens já em consulta por outra chamada ficam de fora. */
    public void check(List<UUID> ids) {
        for (int i = 0; i < ids.size(); i++) {
            UUID id = ids.get(i);
            if (!running.add(id)) continue;
            try {
                checkOne(id);
            } catch (RuntimeException e) {
                log.warn("Consulta de preço falhou para {}: {}", id, e.getMessage());
            } finally {
                running.remove(id);
            }
            if (i < ids.size() - 1) pause();
        }
    }

    /**
     * Recalcula ofertas e menor preço com os preços anotados à mão, sem consultar de novo (dentro da transação de quem
     * chama).
     */
    void refreshOffers(PriceWatch w) {
        List<Offer> fetched = offers(w).stream().filter(o -> o.source() != Source.MANUAL).toList();
        setOffers(w, fetched, w.getStoreCount() == null ? 0 : w.getStoreCount());
        updatePlan(w);
    }

    List<Offer> offers(PriceWatch w) {
        if (w.getOffers() == null) return List.of();
        try {
            return json.readValue(w.getOffers(), OFFERS);
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private void checkOne(UUID id) {
        Target target = transaction.execute(status -> watches.findById(id).map(w -> {
            Map<UUID, String> urls = new HashMap<>();
            links.findByWatchIdOrderByCreatedAtAsc(id).forEach(l -> urls.put(l.getId(), l.getUrl()));
            return new Target(w.getName(), w.getBuscapeUrl(), w.getImageKey() == null, urls);
        }).orElse(null));
        if (target == null) return;
        Fetched fetched = fetch(target);
        transaction.executeWithoutResult(status -> apply(id, fetched));
    }

    private Fetched fetch(Target target) {
        String buscapeUrl = target.buscapeUrl();
        if (buscapeUrl == null) {
            buscapeUrl = references.findProductUrl(target.name()).filter(PriceCheckService::isProductPage).orElse(null);
        }
        BuscapeProduct product = null;
        boolean failed = false;
        if (buscapeUrl != null) {
            try {
                Response page = http.get(URI.create(buscapeUrl), "text/html", MAX_PAGE_BYTES, type -> type.contains("html"));
                String html = new String(page.body(), page.charset() == null ? StandardCharsets.UTF_8 : page.charset());
                product = BuscapeProduct.parse(html).orElse(null);
            } catch (FetchException e) {
                log.debug("Página do Buscapé falhou: {}", e.getMessage());
                failed = true;
            }
        }
        Map<UUID, StorePrice> linkPrices = new HashMap<>();
        target.links().forEach((linkId, url) -> {
            try {
                linkPrices.put(linkId, stores.storePrice(url));
            } catch (BusinessException e) {
                linkPrices.put(linkId, new StorePrice(null, null, null, true));
            }
        });
        byte[] image = target.needsImage() && product != null ? download(product.imageUrls()) : null;
        return new Fetched(buscapeUrl, product, failed, linkPrices, image);
    }

    private void apply(UUID id, Fetched f) {
        PriceWatch w = watches.findById(id).orElse(null);
        if (w == null) return;
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate today = LocalDate.now(clock);
        BigDecimal previous = w.getBestPrice();
        if (f.buscapeUrl() != null) w.setBuscapeUrl(f.buscapeUrl());
        List<Offer> fetched = new ArrayList<>();
        int storeCount = 0;
        if (f.product() != null) {
            storeCount = f.product().storeCount();
            for (BuscapeProduct.Offer o : f.product().offers()) {
                fetched.add(new Offer(o.store(), o.price(), o.totalPrice(), o.url(), Source.BUSCAPE, today));
                point(id, today, o.store(), Source.BUSCAPE, o.price(), o.url(), true);
            }
            for (BuscapeProduct.HistoryDay d : f.product().history()) {
                if (d.day().isBefore(today)) point(id, d.day(), d.store(), Source.BUSCAPE, d.price(), null, false);
            }
        }
        for (PriceLink link : links.findByWatchIdOrderByCreatedAtAsc(id)) {
            StorePrice sp = f.links().get(link.getId());
            if (sp == null) continue;
            String store = sp.site() != null ? limit(sp.site()) : link.getStore();
            link.checked(store, sp.blocked() ? null : sp.price(), sp.blocked() ? "A loja não liberou o preço." : null, now);
            if (sp.blocked() || store == null) continue;
            fetched.add(new Offer(store, sp.price(), null, link.getUrl(), Source.LINK, today));
            point(id, today, store, Source.LINK, sp.price(), link.getUrl(), true);
        }
        setOffers(w, fetched, storeCount);
        String error = w.getBestPrice() != null ? null : f.buscapeFailed() ? "Buscapé indisponível agora. Tente mais tarde."
                : "Nenhum preço encontrado. Cole o link de uma loja ou anote o preço.";
        w.checked(error, now);
        if (f.image() != null && w.getImageKey() == null) {
            try {
                w.setImageKey(images.saveDownload(f.image()));
            } catch (BusinessException | IllegalArgumentException e) {
                log.debug("Foto do produto inválida: {}", e.getMessage());
            }
        }
        alert(w, previous);
        updatePlan(w);
    }

    private void setOffers(PriceWatch w, List<Offer> fetched, int storeCount) {
        LocalDate today = LocalDate.now(clock);
        List<PricePoint> manual = points.findByWatchIdAndSourceAndDayGreaterThanEqual(w.getId(), Source.MANUAL,
                today.minusDays(PriceBook.MANUAL_DAYS));
        List<Offer> offers = PriceBook.merge(fetched, manual, today);
        Offer best = PriceBook.best(offers);
        long distinct = offers.stream().map(Offer::store).distinct().count();
        w.setOffers(json.writeValueAsString(offers), best == null ? null : best.price(), best == null ? null : best.store(),
                best == null ? null : best.url(), offers.isEmpty() ? null : (int) Math.max(storeCount, distinct));
    }

    private void point(UUID watchId, LocalDate day, String store, Source source, BigDecimal price, String url, boolean overwrite) {
        PricePoint point = points.findByWatchIdAndDayAndStoreAndSource(watchId, day, store, source).orElse(null);
        if (point != null && !overwrite) return;
        if (point == null) point = new PricePoint(watchId, day, store, source, null);
        point.setPrice(price, url);
        points.save(point);
    }

    private void alert(PriceWatch w, BigDecimal previous) {
        BigDecimal best = w.getBestPrice();
        if (PriceBook.resetsAlert(best, w.getAlertedPrice(), w.getDropPercent())) w.setAlertedPrice(null);
        Alert alert = PriceBook.alert(best, previous, w.getTargetPrice(), w.getDropPercent(), w.getAlertedPrice());
        if (alert == null) return;
        w.setAlertedPrice(best);
        Map<String, String> data = new HashMap<>();
        data.put("watchId", w.getId().toString());
        data.put("name", w.getName());
        data.put("kind", alert.name());
        data.put("amount", best.toPlainString());
        if (previous != null) {
            data.put("previous", previous.toPlainString());
            BigDecimal percent = PriceBook.percent(best, previous);
            if (percent != null) data.put("percent", percent.negate().stripTrailingZeros().toPlainString());
        }
        if (w.getBestStore() != null) data.put("store", w.getBestStore());
        Set<UUID> to = new LinkedHashSet<>();
        to.add(w.getUserId());
        if (w.getGroupId() != null && w.isNotifyGroup()) {
            members.findByGroupIdOrderByCreatedAtAsc(w.getGroupId()).stream().map(GroupMember::getUserId).forEach(to::add);
        }
        for (UUID userId : to) notifications.notify(userId, NotificationType.PRICE_ALERT, null, w.getGroupId(), data);
    }

    private void updatePlan(PriceWatch w) {
        if (w.getPlanItemId() != null && w.isUpdatePlan() && w.getBestPrice() != null) {
            plans.updateAmount(w.getPlanItemId(), w.getBestPrice());
        }
    }

    private byte[] download(List<String> urls) {
        for (String url : urls) {
            try {
                return http.get(SafeHttpClient.parseUserUrl(url), "image/jpeg,image/png,image/webp;q=0.9", MAX_IMAGE_BYTES,
                        type -> type.startsWith("image/")).body();
            } catch (FetchException | IllegalArgumentException e) {
                log.debug("Foto do produto ignorada: {}", e.getMessage());
            }
        }
        return null;
    }

    private static boolean isProductPage(String url) {
        return url.startsWith(BuscapeProduct.ORIGIN + "/") && !url.contains("/search") && !url.contains("/lead");
    }

    private static String limit(String value) {
        return value.length() > 80 ? value.substring(0, 80) : value;
    }

    private static void pause() {
        try {
            Thread.sleep(PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
