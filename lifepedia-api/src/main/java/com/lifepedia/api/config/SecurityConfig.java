package com.lifepedia.api.config;

import com.lifepedia.api.auth.TokenService;
import com.lifepedia.api.auth.UserSessionRepository;
import com.lifepedia.api.shared.RateLimiter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.WebUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** JWT HS256 via cookie HttpOnly (navegador) ou {@code Authorization: Bearer}. */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, AuthProperties.class, GoogleProperties.class})
public class SecurityConfig {

    /** Token ignorado aqui, para cookie vencido não bloquear o login. */
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of("/api/auth/login", "/api/auth/register", "/api/auth/google",
            "/api/auth/logout");
    private static final String AUTH_CONFIG = "/api/auth/config";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimiter rateLimiter, AuthProperties authProperties)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_PATHS.toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.GET, AUTH_CONFIG).permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .addFilterBefore(new SecurityFilters.RequireCustomHeaderFilter(), BearerTokenAuthenticationFilter.class)
                .addFilterBefore(new SecurityFilters.AuthRateLimitFilter(rateLimiter, authProperties),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    BearerTokenResolver bearerTokenResolver(AuthProperties properties) {
        DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
        return request -> {
            if (PUBLIC_AUTH_PATHS.contains(request.getRequestURI()) || AUTH_CONFIG.equals(request.getRequestURI())) return null;
            String fromHeader = headerResolver.resolve(request);
            if (fromHeader != null) return fromHeader;
            Cookie cookie = WebUtils.getCookie(request, properties.cookieName());
            return cookie != null && !cookie.getValue().isBlank() ? cookie.getValue() : null;
        };
    }

    @Bean
    SecretKey jwtSecretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    /**
     * Além de assinatura, validade e emissor: a sessão ({@code sid}) ainda existe. Logout e conta excluída apagam a
     * sessão, então o token antigo dá 401.
     */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties, UserSessionRepository sessions) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> sessionActive = jwt -> {
            try {
                UUID sid = UUID.fromString(jwt.getClaimAsString(TokenService.SESSION_CLAIM));
                if (sessions.existsByIdAndExpiresAtAfter(sid, Instant.now())) return OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // sid ausente ou inválido
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Sessão encerrada.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), sessionActive));
        return decoder;
    }

    /** BCrypt 12; hashes antigos são atualizados no login. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", SecurityFilters.CSRF_HEADER, "X-Auth-Mode"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
