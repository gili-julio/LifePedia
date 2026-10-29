package com.lifepedia.api.auth;

import com.lifepedia.api.auth.AuthDtos.AuthConfig;
import com.lifepedia.api.auth.AuthDtos.AuthResponse;
import com.lifepedia.api.auth.AuthDtos.GoogleLoginRequest;
import com.lifepedia.api.auth.AuthDtos.LoginRequest;
import com.lifepedia.api.auth.AuthDtos.RegisterRequest;
import com.lifepedia.api.config.AuthProperties;
import com.lifepedia.api.config.GoogleProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.time.Instant;

/** Sessão em cookie HttpOnly; com {@code X-Auth-Mode: token} o JWT vem no corpo. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String TOKEN_MODE = "token";
    private static final String BEARER = "Bearer ";

    private final AuthService authService;
    private final TokenService tokenService;
    private final SessionCookies cookies;
    private final AuthProperties authProperties;
    private final GoogleProperties googleProperties;

    public AuthController(AuthService authService, TokenService tokenService, SessionCookies cookies,
                          AuthProperties authProperties, GoogleProperties googleProperties) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.cookies = cookies;
        this.authProperties = authProperties;
        this.googleProperties = googleProperties;
    }

    /** Público: o que a tela de login precisa saber (Client ID do Google é público por natureza). */
    @GetMapping("/config")
    public AuthConfig config() {
        return new AuthConfig(googleProperties.enabled() ? googleProperties.clientId() : null);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 @RequestHeader(name = "X-Auth-Mode", required = false) String mode) {
        return respond(HttpStatus.CREATED, authService.register(request), mode);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              @RequestHeader(name = "X-Auth-Mode", required = false) String mode) {
        return respond(HttpStatus.OK, authService.login(request), mode);
    }

    /** Entra (ou cria a conta) com o ID token do botão do Google. */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest request,
                                               @RequestHeader(name = "X-Auth-Mode", required = false) String mode) {
        return respond(HttpStatus.OK, authService.google(request), mode);
    }

    /** Encerra a sessão no banco (o token deixa de valer) e apaga o cookie. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        Cookie cookie = WebUtils.getCookie(request, authProperties.cookieName());
        tokenService.revoke(header != null && header.startsWith(BEARER) ? header.substring(BEARER.length())
                : cookie != null ? cookie.getValue() : null);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.expired().toString()).build();
    }

    private ResponseEntity<AuthResponse> respond(HttpStatus status, AuthResponse response, String mode) {
        if (TOKEN_MODE.equalsIgnoreCase(mode)) {
            return ResponseEntity.status(status).body(response);
        }
        Duration maxAge = response.remember() ? Duration.between(Instant.now(), response.expiresAt()) : null;
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.session(response.token(), maxAge).toString())
                .body(response.withoutToken());
    }
}
