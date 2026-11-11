package com.lifepedia.api.architecture;

import com.lifepedia.api.architecture.ArchitectureDtos.PublicProject;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Link público da maquete (sem login, liberado em {@code SecurityConfig}): só leitura, só a versão atual e as vistas,
 * com limite por IP. O token (32 bytes aleatórios) é o segredo; vencido ou apagado → 404.
 */
@RestController
@RequestMapping("/api/public/architecture/{token}")
public class PublicModelController {

    private static final int REQUESTS_PER_WINDOW = 120;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final ArchShareService sharing;
    private final RateLimiter rateLimiter;

    public PublicModelController(ArchShareService sharing, RateLimiter rateLimiter) {
        this.sharing = sharing;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public PublicProject project(@PathVariable String token, HttpServletRequest request) {
        limit(request);
        return sharing.publicProject(token);
    }

    /** Sem cache longo: a versão atual pode mudar com o mesmo link (o ETag evita baixar de novo). */
    @GetMapping("/model")
    public ResponseEntity<Resource> model(@PathVariable String token, HttpServletRequest request) {
        limit(request);
        Resource model = sharing.publicModel(token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ArchitectureController.GLB))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .eTag(model.getFilename())
                .header("X-Content-Type-Options", "nosniff")
                .body(model);
    }

    private void limit(HttpServletRequest request) {
        if (!rateLimiter.tryAcquire("arch-public:" + request.getRemoteAddr(), REQUESTS_PER_WINDOW, WINDOW)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitos acessos seguidos. Tente de novo em alguns minutos.");
        }
    }
}
