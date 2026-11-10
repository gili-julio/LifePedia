package com.lifepedia.api.admin;

import com.lifepedia.api.admin.AdminDtos.AdminUser;
import com.lifepedia.api.admin.AdminDtos.AdminUserDetail;
import com.lifepedia.api.admin.AdminDtos.UserFilter;
import com.lifepedia.api.admin.AuditEntry.Action;
import com.lifepedia.api.auth.TokenService;
import com.lifepedia.api.config.AdminProperties;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.PageResponse;
import com.lifepedia.api.shared.Paging;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Usuários no painel: lista, detalhe (só contagens), admin, bloqueio e sessões. */
@Service
public class AdminUserService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "lower(u.name)",
            "createdAt", "u.createdAt",
            "lastSeenAt", "coalesce(u.lastSeenAt, u.createdAt)");
    private static final int MAX_TERM = 100;

    private final UserRepository users;
    private final AdminStats stats;
    private final AuditService audit;
    private final TokenService tokens;
    private final AdminProperties properties;

    public AdminUserService(UserRepository users, AdminStats stats, AuditService audit, TokenService tokens,
                            AdminProperties properties) {
        this.users = users;
        this.stats = stats;
        this.audit = audit;
        this.tokens = tokens;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUser> page(UUID viewerId, String q, UserFilter filter, int page, int size, String sort) {
        String term = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (term.length() > MAX_TERM) term = term.substring(0, MAX_TERM);
        term = term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return PageResponse.of(users.adminPage(term, (filter == null ? UserFilter.ALL : filter).name(),
                        Paging.pageable(page, size, sort, SORT_FIELDS, "createdAt,desc", "u.id")),
                items -> items.stream().map(u -> toAdminUser(u, viewerId)).toList());
    }

    @Transactional(readOnly = true)
    public AdminUserDetail detail(UUID viewerId, UUID id) {
        User user = find(id);
        return new AdminUserDetail(toAdminUser(user, viewerId), user.getGoogleSub() != null, user.hasPassword(),
                user.getTermsVersion(), user.getTermsAcceptedAt(), user.hasAcceptedTerms(), user.getBlockedAt(),
                stats.activeSessions(id), stats.pushDevices(id), stats.userCounts(id));
    }

    @Transactional
    public AdminUser setAdmin(UUID actorId, UUID id, boolean admin) {
        User actor = find(actorId);
        User user = find(id);
        if (user.isAdmin() == admin) return toAdminUser(user, actorId);
        if (!admin && properties.isFixed(user.getEmail())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Esta é a conta admin definida no servidor e não perde o acesso.");
        }
        if (!admin && user.getId().equals(actorId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Você não pode tirar o seu próprio acesso de admin.");
        }
        user.setAdmin(admin);
        audit.record(actor, admin ? Action.ADMIN_GRANTED : Action.ADMIN_REVOKED, user, null);
        return toAdminUser(user, actorId);
    }

    /** Bloquear também encerra todas as sessões (o token deixa de valer na hora). */
    @Transactional
    public AdminUser setBlocked(UUID actorId, UUID id, boolean blocked) {
        User actor = find(actorId);
        User user = find(id);
        if (user.isBlocked() == blocked) return toAdminUser(user, actorId);
        if (blocked && user.getId().equals(actorId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Você não pode bloquear a sua própria conta.");
        }
        if (blocked && properties.isFixed(user.getEmail())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "A conta admin definida no servidor não pode ser bloqueada.");
        }
        user.setBlocked(blocked);
        if (blocked) tokens.revokeAll(user.getId());
        audit.record(actor, blocked ? Action.USER_BLOCKED : Action.USER_UNBLOCKED, user, null);
        return toAdminUser(user, actorId);
    }

    @Transactional
    public void revokeSessions(UUID actorId, UUID id) {
        User actor = find(actorId);
        User user = find(id);
        if (user.getId().equals(actorId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Para sair da sua conta, use Sair no menu.");
        }
        tokens.revokeAll(user.getId());
        audit.record(actor, Action.SESSIONS_REVOKED, user, null);
    }

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }

    private AdminUser toAdminUser(User u, UUID viewerId) {
        return new AdminUser(u.getId(), u.getName(), u.getUsername(), u.getEmail(), u.imageUrl(), u.getCreatedAt(),
                u.getLastSeenAt(), u.isAdmin(), u.isBlocked(), properties.isFixed(u.getEmail()), u.getId().equals(viewerId));
    }
}
