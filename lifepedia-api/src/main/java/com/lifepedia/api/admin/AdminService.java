package com.lifepedia.api.admin;

import com.lifepedia.api.admin.AdminDtos.AiOverview;
import com.lifepedia.api.admin.AdminDtos.Audience;
import com.lifepedia.api.admin.AdminDtos.BroadcastRequest;
import com.lifepedia.api.admin.AdminDtos.BroadcastResponse;
import com.lifepedia.api.admin.AdminDtos.Modules;
import com.lifepedia.api.admin.AdminDtos.Overview;
import com.lifepedia.api.admin.AdminDtos.ServiceLimit;
import com.lifepedia.api.admin.AuditEntry.Action;
import com.lifepedia.api.config.AiProperties;
import com.lifepedia.api.config.GoogleProperties;
import com.lifepedia.api.config.IntegrationProperties;
import com.lifepedia.api.delivery.TrackingQuota;
import com.lifepedia.api.shared.ai.AiUsageLog;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Números do painel e notificação em massa. */
@Service
public class AdminService {

    static final int BROADCASTS_PER_DAY = 5;
    static final long GOOGLE_UNVERIFIED_USERS = 100;

    private final AdminStats stats;
    private final AiProperties ai;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;
    private final RateLimiter rateLimiter;
    private final TrackingQuota tracking;
    private final GoogleProperties google;
    private final IntegrationProperties integrations;

    public AdminService(AdminStats stats, AiProperties ai, UserRepository users, NotificationService notifications,
                        AuditService audit, RateLimiter rateLimiter, TrackingQuota tracking, GoogleProperties google,
                        IntegrationProperties integrations) {
        this.stats = stats;
        this.ai = ai;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.tracking = tracking;
        this.google = google;
        this.integrations = integrations;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        return stats.overview();
    }

    @Transactional(readOnly = true)
    public Modules modules() {
        return new Modules(stats.totalUsers(), stats.modules());
    }

    @Transactional(readOnly = true)
    public AiOverview ai() {
        long[] usersAndDuration = stats.aiUsersAndDuration();
        return new AiOverview(ai.enabled(), ai.model(), ai.timeout().toSeconds(), ai.dailyLimit(), stats.aiLast24h(),
                stats.aiRunning(), usersAndDuration[0], usersAndDuration[1], stats.aiOutcomes(), stats.aiDaily());
    }

    /** Limites dos serviços externos (17TRACK, Gemini, Google Agenda). Fora de transação: lê a cota do 17TRACK. */
    public List<ServiceLimit> limits() {
        List<ServiceLimit> items = new ArrayList<>();
        TrackingQuota.Usage track = tracking.current();
        if (track.quota() != null) {
            items.add(new ServiceLimit("TRACK17", true, "TOTAL", track.quota().used(), track.quota().total(), null));
            items.add(new ServiceLimit("TRACK17_DAILY", true, "DAY", track.quota().today(), track.quota().daily(), null));
        } else {
            items.add(new ServiceLimit("TRACK17", track.enabled(), "TOTAL", null, null, track.error()));
        }
        long cap = ai.dailyLimit();
        items.add(new ServiceLimit("GEMINI_IMPORT", ai.enabled(), "DAY", stats.aiUsedLastDay(AiUsageLog.IMPORT), cap, null));
        items.add(new ServiceLimit("GEMINI_DAILY", ai.enabled(), "DAY", stats.aiUsedLastDay(AiUsageLog.DEV_DAILY), cap, null));
        boolean calendar = google.enabled() && integrations.googleClientSecret() != null && !integrations.googleClientSecret().isBlank();
        items.add(new ServiceLimit("GOOGLE_CALENDAR", calendar, "PEOPLE", stats.googleCalendarUsers(), GOOGLE_UNVERIFIED_USERS, null));
        return items;
    }

    /** Notificação (com push) para todas as contas liberadas ou só para os admins; até 5 por dia por admin. */
    @Transactional
    public BroadcastResponse broadcast(UUID actorId, BroadcastRequest request) {
        User actor = users.findById(actorId).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        if (!rateLimiter.tryAcquire("admin-broadcast:" + actorId, BROADCASTS_PER_DAY, Duration.ofDays(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de " + BROADCASTS_PER_DAY + " notificações em massa por dia atingido.");
        }
        List<UUID> ids = users.findActiveIds(request.audience() == Audience.ADMINS);
        notifications.notifyAll(ids, NotificationType.SYSTEM_MESSAGE, actorId,
                Map.of("title", request.title().trim(), "body", request.body().trim()));
        audit.record(actor, Action.BROADCAST_SENT, null,
                (request.audience() == Audience.ADMINS ? "Só admins" : "Todos") + " (" + ids.size() + "): " + request.title().trim());
        return new BroadcastResponse(ids.size());
    }
}
