package com.lifepedia.api.admin;

import com.lifepedia.api.admin.AuditEntry.Action;
import com.lifepedia.api.config.AdminProperties;
import com.lifepedia.api.user.UserRegisteredEvent;
import com.lifepedia.api.user.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A conta do {@code ADMIN_EMAIL} é sempre admin: ao subir a API e quando ela se cadastra. */
@Component
public class AdminBootstrap {

    private final AdminProperties properties;
    private final UserRepository users;
    private final AuditService audit;

    public AdminBootstrap(AdminProperties properties, UserRepository users, AuditService audit) {
        this.properties = properties;
        this.users = users;
        this.audit = audit;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        promoteFixed();
    }

    /** Cadastro (senha ou Google): mesma transação, então a resposta do cadastro já vem com {@code admin}. */
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        users.findById(event.userId()).filter(user -> properties.isFixed(user.getEmail()) && !user.isAdmin()).ifPresent(user -> {
            user.setAdmin(true);
            audit.record(null, Action.ADMIN_FIXED, user, null);
        });
    }

    @Transactional
    public void promoteFixed() {
        String email = properties.normalizedEmail();
        if (email == null) return;
        users.findByEmail(email).filter(user -> !user.isAdmin()).ifPresent(user -> {
            user.setAdmin(true);
            audit.record(null, Action.ADMIN_FIXED, user, null);
        });
    }
}
