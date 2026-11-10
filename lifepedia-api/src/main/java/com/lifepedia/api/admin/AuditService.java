package com.lifepedia.api.admin;

import com.lifepedia.api.admin.AdminDtos.AuditResponse;
import com.lifepedia.api.admin.AuditEntry.Action;
import com.lifepedia.api.shared.PageResponse;
import com.lifepedia.api.shared.Paging;
import com.lifepedia.api.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Registro das ações de administração; só leitura para os admins. */
@Service
public class AuditService {

    static final String SYSTEM = "Servidor (ADMIN_EMAIL)";
    private static final Map<String, String> SORT_FIELDS = Map.of("createdAt", "a.createdAt");

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    /** Na transação da ação: se a ação falhar, o registro some junto. */
    @Transactional
    public void record(User actor, Action action, User target, String detail) {
        repository.save(new AuditEntry(actor == null ? null : actor.getId(), actor == null ? SYSTEM : actor.getName(), action,
                target == null ? null : target.getId(), target == null ? null : target.getName(), detail));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditResponse> page(int page, int size) {
        return PageResponse.of(repository.page(Paging.pageable(page, size, null, SORT_FIELDS, "createdAt,desc", "a.id")),
                items -> items.stream().map(AuditResponse::from).toList());
    }
}
