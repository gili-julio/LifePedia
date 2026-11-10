package com.lifepedia.api.admin;

import com.lifepedia.api.user.UserRepository;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/** Rotas {@code /api/admin/**}: só admin com conta liberada, conferido no banco a cada requisição (tirar admin vale na hora). */
@Component
public class AdminAccess implements AuthorizationManager<RequestAuthorizationContext> {

    private final UserRepository users;

    public AdminAccess(UserRepository users) {
        this.users = users;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        return new AuthorizationDecision(authentication.get() instanceof JwtAuthenticationToken jwt
                && jwt.isAuthenticated() && isAdmin(jwt.getToken().getSubject()));
    }

    private boolean isAdmin(String subject) {
        try {
            return users.isActiveAdmin(UUID.fromString(subject));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
