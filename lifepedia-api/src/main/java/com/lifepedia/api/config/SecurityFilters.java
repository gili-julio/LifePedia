package com.lifepedia.api.config;

import com.lifepedia.api.shared.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/** Filtros extras da cadeia do Spring Security. */
final class SecurityFilters {

    static final String CSRF_HEADER = "X-Requested-With";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private SecurityFilters() {
    }

    /** CSRF: escrita sem {@code Authorization} exige {@code X-Requested-With}. */
    static final class RequireCustomHeaderFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            boolean unsafe = !SAFE_METHODS.contains(request.getMethod());
            boolean usesBearerHeader = request.getHeader(HttpHeaders.AUTHORIZATION) != null;
            if (unsafe && !usesBearerHeader && request.getHeader(CSRF_HEADER) == null) {
                writeProblem(response, HttpStatus.FORBIDDEN, "Requisição bloqueada (cabeçalho " + CSRF_HEADER + " ausente).");
                return;
            }
            chain.doFilter(request, response);
        }
    }

    /** Limite de login/cadastro por IP. */
    static final class AuthRateLimitFilter extends OncePerRequestFilter {

        private final RateLimiter limiter;
        private final AuthProperties properties;

        AuthRateLimitFilter(RateLimiter limiter, AuthProperties properties) {
            this.limiter = limiter;
            this.properties = properties;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getRequestURI();
            return !HttpMethod.POST.matches(request.getMethod())
                    || !(path.equals("/api/auth/login") || path.equals("/api/auth/register"));
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String key = "auth-ip:" + request.getRemoteAddr();
            if (!limiter.tryAcquire(key, properties.ipLimit(), properties.ipWindow())) {
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(properties.ipWindow().toSeconds()));
                writeProblem(response, HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Aguarde alguns minutos.");
                return;
            }
            chain.doFilter(request, response);
        }
    }

    static void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json;charset=UTF-8");
        String escaped = detail.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"status\":" + status.value() + ",\"title\":\"" + status.getReasonPhrase()
                + "\",\"detail\":\"" + escaped + "\"}");
    }
}
