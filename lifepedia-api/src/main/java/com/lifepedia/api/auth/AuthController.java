package com.lifepedia.api.auth;

import com.lifepedia.api.auth.AuthDtos.AuthResponse;
import com.lifepedia.api.auth.AuthDtos.LoginRequest;
import com.lifepedia.api.auth.AuthDtos.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/** Sessão em cookie HttpOnly; com {@code X-Auth-Mode: token} o JWT vem no corpo. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String TOKEN_MODE = "token";

    private final AuthService authService;
    private final SessionCookies cookies;

    public AuthController(AuthService authService, SessionCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
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

    /** Apaga o cookie de sessão. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.expired().toString()).build();
    }

    private ResponseEntity<AuthResponse> respond(HttpStatus status, AuthResponse response, String mode) {
        if (TOKEN_MODE.equalsIgnoreCase(mode)) {
            return ResponseEntity.status(status).body(response);
        }
        Duration maxAge = Duration.between(Instant.now(), response.expiresAt());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.session(response.token(), maxAge).toString())
                .body(response.withoutToken());
    }
}
