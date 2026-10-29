package com.lifepedia.api.notification.push;

import com.lifepedia.api.config.PushProperties;
import com.lifepedia.api.notification.push.PushDtos.SubscriptionRequest;
import com.lifepedia.api.shared.BusinessException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Aparelhos inscritos e envio do Web Push (depois do commit, fora da requisição). */
@Service
public class PushService {

    static final int MAX_PER_USER = 10;
    private static final int TTL_SECONDS = 86_400;
    private static final Duration VAPID_VALIDITY = Duration.ofHours(12);
    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final PushSubscriptionRepository repository;
    private final PushGateway gateway;
    private final PushProperties properties;
    private final JsonMapper json;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public PushService(PushSubscriptionRepository repository, PushGateway gateway, PushProperties properties, JsonMapper json) {
        if (properties.enabled() && !PushCrypto.keyPairMatches(properties.publicKey(), properties.privateKey())) {
            throw new IllegalStateException("VAPID_PUBLIC_KEY e VAPID_PRIVATE_KEY não formam um par de chaves P-256.");
        }
        this.repository = repository;
        this.gateway = gateway;
        this.properties = properties;
        this.json = json;
    }

    /** Chave pública VAPID ou nula (push desligado). */
    public String publicKey() {
        return properties.enabled() ? properties.publicKey() : null;
    }

    /**
     * Liga este aparelho à conta e à sessão atual.
     *
     * @return se o aparelho recebe push desta conta (sincronização automática não cria nem toma de outra pessoa)
     */
    @Transactional
    public boolean subscribe(UUID userId, UUID sessionId, SubscriptionRequest request) {
        if (!properties.enabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Notificações push não estão configuradas.");
        }
        if (!allowed(request.endpoint())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Serviço de notificação não suportado.");
        }
        if (!PushCrypto.validPublicKey(request.keys().p256dh()) || !PushCrypto.validAuth(request.keys().auth())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Chaves de notificação inválidas.");
        }
        boolean auto = Boolean.TRUE.equals(request.auto());
        Optional<PushSubscription> existing = repository.findByEndpoint(request.endpoint());
        if (existing.isPresent()) {
            PushSubscription subscription = existing.get();
            if (auto && !subscription.getUserId().equals(userId)) return false;
            subscription.attach(userId, sessionId, request.keys().p256dh(), request.keys().auth());
            if (!auto) welcome(subscription);
            return true;
        }
        if (auto) return false;
        PushSubscription subscription = repository.save(
                new PushSubscription(userId, sessionId, request.endpoint(), request.keys().p256dh(), request.keys().auth()));
        List<PushSubscription> mine = repository.findByUserIdOrderByUpdatedAtDesc(userId);
        if (mine.size() > MAX_PER_USER) repository.deleteAll(mine.subList(MAX_PER_USER, mine.size()));
        welcome(subscription);
        return true;
    }

    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        repository.findByEndpoint(endpoint).filter(s -> s.getUserId().equals(userId)).ifPresent(repository::delete);
    }

    /** Agenda o envio para os aparelhos com sessão válida de cada pessoa; falha no envio nunca afeta a ação. */
    public void send(Collection<UUID> userIds, PushMessage message) {
        if (!properties.enabled() || userIds.isEmpty()) return;
        List<UUID> ids = List.copyOf(userIds);
        afterCommit(() -> deliver(ids, message));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /** Confirma no aparelho que acabou de ativar (aparece mesmo com o app aberto: tag {@code test}). */
    private void welcome(PushSubscription subscription) {
        PushMessage message = new PushMessage("LifePedia", "Notificações ativadas neste aparelho.", "/perfil", "test", false);
        afterCommit(() -> deliver(subscription, json.writeValueAsBytes(message), false));
    }

    private void afterCommit(Runnable work) {
        Runnable task = () -> executor.execute(work);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void deliver(List<UUID> userIds, PushMessage message) {
        try {
            byte[] payload = json.writeValueAsBytes(message);
            for (PushSubscription subscription : repository.findActive(userIds, Instant.now())) {
                deliver(subscription, payload, message.urgent());
            }
        } catch (RuntimeException e) {
            log.warn("Falha ao enviar push: {}", e.getMessage());
        }
    }

    private void deliver(PushSubscription subscription, byte[] payload, boolean urgent) {
        URI uri = URI.create(subscription.getEndpoint());
        if (!allowed(subscription.getEndpoint())) {
            repository.deleteById(subscription.getId());
            return;
        }
        try {
            String authorization = PushCrypto.vapid("https://" + uri.getHost(), properties.subject(), properties.publicKey(),
                    properties.privateKey(), Instant.now().plus(VAPID_VALIDITY).getEpochSecond());
            byte[] body = PushCrypto.encrypt(payload, subscription.getP256dh(), subscription.getAuth());
            int status = gateway.post(subscription.getEndpoint(), body, authorization, TTL_SECONDS, urgent);
            // 404/410: o navegador cancelou a inscrição.
            if (status == 404 || status == 410) repository.deleteById(subscription.getId());
            else if (status >= 400) log.warn("Push recusado por {}: HTTP {}", uri.getHost(), status);
        } catch (IOException e) {
            log.warn("Push não entregue a {}: {}", uri.getHost(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Só serviços de push conhecidos, em https na porta padrão (o endpoint vem do navegador). */
    private boolean allowed(String endpoint) {
        try {
            URI uri = new URI(endpoint);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }
            String lower = host.toLowerCase();
            return properties.allowedHosts() != null && properties.allowedHosts().stream()
                    .anyMatch(allowed -> lower.equals(allowed) || lower.endsWith("." + allowed));
        } catch (Exception e) {
            return false;
        }
    }
}
