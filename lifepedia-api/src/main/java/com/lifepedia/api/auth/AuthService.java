package com.lifepedia.api.auth;

import com.lifepedia.api.auth.AuthDtos.AuthResponse;
import com.lifepedia.api.auth.AuthDtos.LoginRequest;
import com.lifepedia.api.auth.AuthDtos.RegisterRequest;
import com.lifepedia.api.config.AuthProperties;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserRegisteredEvent;
import com.lifepedia.api.user.UserRepository;
import com.lifepedia.api.user.UserResponse;
import com.lifepedia.api.user.UserService;
import com.lifepedia.api.user.Usernames;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    /** Limite do BCrypt; acima disso a senha seria truncada. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ApplicationEventPublisher events;
    private final RateLimiter rateLimiter;
    private final AuthProperties properties;
    /** Usado quando o e-mail não existe, para manter o tempo de resposta. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, UserService userService, PasswordEncoder passwordEncoder, TokenService tokenService,
                       ApplicationEventPublisher events, RateLimiter rateLimiter, AuthProperties properties) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.events = events;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode("lifepedia-dummy-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        requireBcryptCompatible(request.password());
        String email = normalizeEmail(request.email());
        String username = Usernames.require(request.username());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail.");
        }
        userService.requireAvailable(username, null);
        User user = userRepository.save(new User(request.name().trim(), username, email,
                passwordEncoder.encode(request.password())));
        events.publishEvent(new UserRegisteredEvent(user.getId()));
        return toResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        String lockKey = "login-fail:" + email;
        if (rateLimiter.count(lockKey, properties.lockoutWindow()) >= properties.maxFailedLogins()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de login para este e-mail. Tente novamente em alguns minutos.");
        }

        Optional<User> user = userRepository.findByEmail(email);
        boolean passwordOk = isBcryptCompatible(request.password())
                && passwordEncoder.matches(request.password(), user.map(User::getPasswordHash).orElse(dummyHash));

        if (user.isEmpty() || !passwordOk) {
            rateLimiter.record(lockKey);
            throw new BadCredentialsException("invalid credentials");
        }

        rateLimiter.reset(lockKey);
        User found = user.get();
        if (passwordEncoder.upgradeEncoding(found.getPasswordHash())) {
            found.changePasswordHash(passwordEncoder.encode(request.password()));
        }
        return toResponse(found);
    }

    /** Confirma a senha de quem já está logado (ações sensíveis); falhas contam no mesmo limite do login. */
    @Transactional(readOnly = true)
    public void verifyPassword(UUID userId, String password) {
        String lockKey = "password-check:" + userId;
        if (rateLimiter.count(lockKey, properties.lockoutWindow()) >= properties.maxFailedLogins()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas com a senha errada. Tente novamente em alguns minutos.");
        }
        User user = userService.find(userId);
        if (password == null || !isBcryptCompatible(password) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            rateLimiter.record(lockKey);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Senha incorreta.");
        }
        rateLimiter.reset(lockKey);
    }

    private AuthResponse toResponse(User user) {
        TokenService.IssuedToken token = tokenService.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    private static void requireBcryptCompatible(String password) {
        if (!isBcryptCompatible(password)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Senha longa demais (use menos caracteres especiais/acentos).");
        }
    }

    private static boolean isBcryptCompatible(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
