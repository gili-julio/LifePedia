package com.lifepedia.api.auth;

import com.lifepedia.api.auth.AuthDtos.AuthResponse;
import com.lifepedia.api.auth.AuthDtos.GoogleLoginRequest;
import com.lifepedia.api.auth.AuthDtos.LoginRequest;
import com.lifepedia.api.auth.AuthDtos.RegisterRequest;
import com.lifepedia.api.config.AuthProperties;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.user.LegalTerms;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    /** Limite do BCrypt; acima disso a senha seria truncada. */
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final int MAX_NAME = 120;
    private static final Duration RECENT_GOOGLE_LOGIN = Duration.ofMinutes(10);

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ApplicationEventPublisher events;
    private final RateLimiter rateLimiter;
    private final AuthProperties properties;
    private final GoogleTokenVerifier google;
    /** Usado quando o e-mail não existe, para manter o tempo de resposta. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, UserService userService, PasswordEncoder passwordEncoder, TokenService tokenService,
                       ApplicationEventPublisher events, RateLimiter rateLimiter, AuthProperties properties,
                       GoogleTokenVerifier google) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.events = events;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.google = google;
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
        User user = new User(request.name().trim(), username, email, passwordEncoder.encode(request.password()));
        user.acceptTerms(LegalTerms.VERSION);
        user = userRepository.save(user);
        events.publishEvent(new UserRegisteredEvent(user.getId()));
        return toResponse(user, false);
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
        String hash = user.map(User::getPasswordHash).orElse(null);
        // Conta sem senha (criada pelo Google) compara com o hash "dummy" para manter o tempo, mas nunca entra.
        boolean passwordOk = isBcryptCompatible(request.password())
                && passwordEncoder.matches(request.password(), hash == null ? dummyHash : hash)
                && hash != null;

        if (!passwordOk) {
            rateLimiter.record(lockKey);
            throw new BadCredentialsException("invalid credentials");
        }

        rateLimiter.reset(lockKey);
        User found = user.get();
        if (passwordEncoder.upgradeEncoding(found.getPasswordHash())) {
            found.changePasswordHash(passwordEncoder.encode(request.password()));
        }
        return toResponse(found, Boolean.TRUE.equals(request.remember()));
    }

    /**
     * Entra com o Google: pela conta já vinculada, vinculando a conta de mesmo e-mail (só se o Google confirmou o
     * e-mail) ou criando uma conta sem senha e sem @username (o app leva ao perfil para completar).
     */
    @Transactional
    public AuthResponse google(GoogleLoginRequest request) {
        GoogleIdentity identity = google.verify(request.credential());
        User user = userRepository.findByGoogleSub(identity.subject()).orElse(null);
        if (user == null) {
            String email = normalizeEmail(identity.email());
            user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                if (!identity.emailVerified()) {
                    throw new BusinessException(HttpStatus.CONFLICT,
                            "Já existe uma conta com este e-mail. Entre com a senha e vincule o Google no perfil.");
                }
                if (user.getGoogleSub() != null) {
                    throw new BusinessException(HttpStatus.CONFLICT, "Esta conta já está vinculada a outra conta Google.");
                }
                user.setGoogleSub(identity.subject());
            } else {
                user = userRepository.save(User.fromGoogle(googleName(identity), email, identity.subject()));
                events.publishEvent(new UserRegisteredEvent(user.getId()));
            }
        }
        return toResponse(user, Boolean.TRUE.equals(request.remember()));
    }

    @Transactional
    public UserResponse linkGoogle(UUID userId, String credential) {
        GoogleIdentity identity = google.verify(credential);
        User user = userService.find(userId);
        if (identity.subject().equals(user.getGoogleSub())) return UserResponse.from(user);
        if (user.getGoogleSub() != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "Desvincule a conta Google atual antes de vincular outra.");
        }
        if (userRepository.findByGoogleSub(identity.subject()).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Esta conta Google já está vinculada a outro usuário.");
        }
        user.setGoogleSub(identity.subject());
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse unlinkGoogle(UUID userId) {
        User user = userService.find(userId);
        if (!user.hasPassword()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Sua conta foi criada pelo Google e não tem senha, então não dá para desvincular.");
        }
        user.setGoogleSub(null);
        return UserResponse.from(user);
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
        if (password == null || !user.hasPassword() || !isBcryptCompatible(password)
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            rateLimiter.record(lockKey);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Senha incorreta.");
        }
        rateLimiter.reset(lockKey);
    }

    /** Ação sensível: a senha; em conta sem senha, um login recente no Google com a conta vinculada. */
    @Transactional(readOnly = true)
    public void confirmIdentity(UUID userId, String password, String googleCredential) {
        User user = userService.find(userId);
        if (user.hasPassword()) {
            verifyPassword(userId, password);
            return;
        }
        if (googleCredential == null || googleCredential.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Confirme com a sua conta Google.");
        }
        GoogleIdentity identity = google.verify(googleCredential);
        boolean recent = identity.issuedAt() != null && identity.issuedAt().isAfter(Instant.now().minus(RECENT_GOOGLE_LOGIN));
        if (!identity.subject().equals(user.getGoogleSub()) || !recent) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Confirme com a conta Google vinculada.");
        }
    }

    private AuthResponse toResponse(User user, boolean remember) {
        TokenService.IssuedToken token = tokenService.issue(user, remember);
        return new AuthResponse(token.value(), token.expiresAt(), token.remember(), UserResponse.from(user));
    }

    private static String googleName(GoogleIdentity identity) {
        String name = identity.name() == null || identity.name().isBlank()
                ? identity.email().substring(0, identity.email().indexOf('@'))
                : identity.name().trim();
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
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
