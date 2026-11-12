package com.lifepedia.api.delivery;

import com.lifepedia.api.delivery.Track17Client.Registered;
import com.lifepedia.api.delivery.Track17Client.TrackException;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Rastreio pelo 17TRACK: registra códigos novos, lê os eventos (40 por chamada) e avisa as mudanças. */
@Service
public class DeliverySyncService {

    static final Duration INTERVAL = Duration.ofMinutes(50);
    static final int STALL_DAYS = 7;
    static final int ARCHIVE_DAYS = 7;
    private static final int MAX_PER_RUN = 400;
    private static final long PAUSE_MS = 400;
    private static final Set<DeliveryStatus> NOTIFY = Set.of(DeliveryStatus.OUT_FOR_DELIVERY, DeliveryStatus.PICKUP,
            DeliveryStatus.DELIVERED, DeliveryStatus.FAILED, DeliveryStatus.EXCEPTION, DeliveryStatus.EXPIRED);
    private static final Logger log = LoggerFactory.getLogger(DeliverySyncService.class);

    private final DeliveryRepository deliveries;
    private final GroupMemberRepository members;
    private final Track17Client track17;
    private final NotificationService notifications;
    private final TransactionTemplate transaction;
    private final JsonMapper json;
    private final Clock clock;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    public DeliverySyncService(DeliveryRepository deliveries, GroupMemberRepository members, Track17Client track17,
                               NotificationService notifications, PlatformTransactionManager transactions, JsonMapper json,
                               Clock clock) {
        this.deliveries = deliveries;
        this.members = members;
        this.track17 = track17;
        this.notifications = notifications;
        this.transaction = new TransactionTemplate(transactions);
        this.json = json;
        this.clock = clock;
    }

    /** Fora de transação. Entregas já em sincronização por outra chamada ficam de fora. */
    public void sync(List<UUID> ids) {
        if (!track17.enabled() || ids.isEmpty()) return;
        List<UUID> mine = ids.stream().filter(running::add).toList();
        try {
            run(mine);
        } finally {
            mine.forEach(running::remove);
        }
    }

    /** Job: as que estão na vez (não entregues, sem sincronizar há {@link #INTERVAL}). */
    public void syncDue() {
        OffsetDateTime before = OffsetDateTime.now(clock).minus(INTERVAL);
        List<UUID> ids = deliveries.due(before, DeliveryStatus.DELIVERED, PageRequest.of(0, MAX_PER_RUN)).stream()
                .map(Delivery::getId).toList();
        sync(ids);
    }

    /** Entregues há mais de {@link #ARCHIVE_DAYS} dias vão para o histórico. */
    public int archiveDelivered() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Integer archived = transaction.execute(status ->
                deliveries.archiveDelivered(now.minusDays(ARCHIVE_DAYS), now, DeliveryStatus.DELIVERED));
        return archived == null ? 0 : archived;
    }

    private void run(List<UUID> ids) {
        List<Delivery> items = deliveries.findAllById(ids);
        if (items.isEmpty()) return;
        Set<String> toRegister = new LinkedHashSet<>();
        Set<String> codes = new LinkedHashSet<>();
        for (Delivery d : items) {
            codes.add(d.getTrackingCode());
            if (!d.isRegistered()) toRegister.add(d.getTrackingCode());
        }
        Set<String> registered = new HashSet<>();
        Map<String, String> errors = new HashMap<>();
        Map<String, JsonNode> infos = new HashMap<>();
        try {
            for (List<String> batch : batches(toRegister)) {
                Registered result = track17.register(batch);
                registered.addAll(result.accepted());
                errors.putAll(result.rejected());
                pause();
            }
            List<String> readable = codes.stream().filter(c -> !errors.containsKey(c)).toList();
            for (List<String> batch : batches(readable)) {
                infos.putAll(track17.track(batch));
                pause();
            }
        } catch (TrackException e) {
            log.warn("Rastreio falhou: {}", e.getMessage());
            codes.stream().filter(c -> !infos.containsKey(c)).forEach(c -> errors.putIfAbsent(c, e.getMessage()));
        }
        transaction.executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now(clock);
            for (Delivery d : deliveries.findAllById(ids)) {
                String code = d.getTrackingCode();
                if (registered.contains(code)) d.setRegistered(true);
                JsonNode info = infos.get(code);
                if (info != null && !info.isMissingNode() && !info.isNull()) {
                    apply(d, TrackSnapshot.parse(info), now);
                } else if (errors.containsKey(code)) {
                    d.failSync(trim(errors.get(code)), now);
                } else {
                    d.failSync(null, now);
                }
            }
        });
    }

    private void apply(Delivery d, TrackSnapshot snapshot, OffsetDateTime now) {
        boolean first = d.getNotifiedStatus() == null && d.getLatestAt() == null;
        boolean hadTax = d.isTaxPending();
        d.apply(snapshot, json.writeValueAsString(snapshot.events()), now);
        if (first) {
            d.setNotifiedStatus(snapshot.status());
            return;
        }
        if (NOTIFY.contains(snapshot.status()) && snapshot.status() != d.getNotifiedStatus()) {
            notify(d, snapshot.status().name(), Map.of());
        }
        d.setNotifiedStatus(snapshot.status());
        if (snapshot.taxPending() && !hadTax) notify(d, "TAX", Map.of());
        if (snapshot.status() != DeliveryStatus.DELIVERED && snapshot.latestAt() != null && !d.isStallWarned()) {
            long days = ChronoUnit.DAYS.between(snapshot.latestAt(), now);
            if (days >= STALL_DAYS) {
                d.setStallWarned(true);
                notify(d, "STALLED", Map.of("days", String.valueOf(days)));
            }
        }
    }

    private void notify(Delivery d, String status, Map<String, String> extra) {
        Map<String, String> data = new HashMap<>(extra);
        data.put("deliveryId", d.getId().toString());
        data.put("name", d.getName());
        data.put("status", status);
        if (d.getLatestText() != null) data.put("text", d.getLatestText());
        Set<UUID> to = new LinkedHashSet<>();
        to.add(d.getUserId());
        if (d.getGroupId() != null) {
            members.findByGroupIdOrderByCreatedAtAsc(d.getGroupId()).stream().map(GroupMember::getUserId).forEach(to::add);
        }
        for (UUID userId : to) notifications.notify(userId, NotificationType.DELIVERY_UPDATE, null, d.getGroupId(), data);
    }

    private static List<List<String>> batches(Set<String> codes) {
        return batches(new ArrayList<>(codes));
    }

    private static List<List<String>> batches(List<String> codes) {
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < codes.size(); i += Track17Client.BATCH) {
            result.add(codes.subList(i, Math.min(codes.size(), i + Track17Client.BATCH)));
        }
        return result;
    }

    private static void pause() {
        try {
            Thread.sleep(PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String trim(String text) {
        if (text == null) return null;
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
