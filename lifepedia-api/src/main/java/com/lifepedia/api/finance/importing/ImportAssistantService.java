package com.lifepedia.api.finance.importing;

import com.lifepedia.api.config.AiProperties;
import com.lifepedia.api.finance.category.CategoryService;
import com.lifepedia.api.finance.importing.ImportDtos.AssistantConfig;
import com.lifepedia.api.finance.importing.ImportDtos.DraftResponse;
import com.lifepedia.api.finance.recurring.RecurringEntryService;
import com.lifepedia.api.finance.transaction.TransactionService;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.shared.ai.GeminiClient;
import com.lifepedia.api.shared.ai.GeminiClient.AiException;
import com.lifepedia.api.shared.ai.GeminiClient.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Prompt da importação e leitura de documentos pelo Gemini, em segundo plano. Os arquivos só ficam na memória enquanto
 * a leitura roda; do resultado fica só a lista de lançamentos, no rascunho.
 */
@Service
public class ImportAssistantService {

    private static final Logger log = LoggerFactory.getLogger(ImportAssistantService.class);
    /** Falha não conta nos limites da conta, mas custa uma chamada. */
    private static final int HOURLY_ATTEMPTS = 5;
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);
    private static final String DAILY_KEY = "import-ai:all";
    /** Cada leitura segura os arquivos na memória até o Gemini responder. */
    private static final int MAX_RUNNING = 3;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final GeminiClient gemini;
    private final AiProperties properties;
    private final ImportDraftService drafts;
    private final CategoryService categories;
    private final RecurringEntryService recurring;
    private final TransactionService transactions;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final Semaphore running = new Semaphore(MAX_RUNNING);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ImportAssistantService(GeminiClient gemini, AiProperties properties, ImportDraftService drafts,
                                  CategoryService categories, RecurringEntryService recurring,
                                  TransactionService transactions, RateLimiter rateLimiter, Clock clock) {
        this.gemini = gemini;
        this.properties = properties;
        this.drafts = drafts;
        this.categories = categories;
        this.recurring = recurring;
        this.transactions = transactions;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public AssistantConfig config(UUID userId) {
        return new AssistantConfig(gemini.enabled(), ImportDocuments.MAX_FILES, ImportDocuments.MAX_TOTAL_BYTES,
                ImportDocuments.MAX_PAGES, quota(userId));
    }

    public String prompt(UUID userId) {
        return ImportPrompt.build(categories.list(userId, null), recurring.list(userId, null),
                transactions.descriptions(userId), LocalDate.now(clock));
    }

    /** Confere tudo, grava a leitura e responde; o Gemini é chamado em segundo plano. */
    public DraftResponse analyze(UUID userId, List<MultipartFile> files) {
        if (!gemini.enabled()) throw new BusinessException(HttpStatus.NOT_FOUND, "A leitura pelo site não está ligada.");
        drafts.requireNoneOpen(userId);
        ImportDocuments.checkLimits(files);
        requireQuota(userId);
        if (!running.tryAcquire()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Muitas leituras agora. Tente de novo em instantes.");
        }
        boolean started = false;
        try {
            List<Attachment> documents = ImportDocuments.read(files);
            requireAttempt(userId);
            String prompt = prompt(userId);
            DraftResponse draft = drafts.start(userId, documents.size());
            executor.execute(() -> read(draft.id(), prompt, documents));
            started = true;
            return draft;
        } finally {
            if (!started) running.release();
        }
    }

    private void read(UUID draftId, String prompt, List<Attachment> documents) {
        try {
            drafts.complete(draftId, gemini.generateJson(prompt, documents));
        } catch (AiException e) {
            drafts.fail(draftId, message(e));
        } catch (RuntimeException e) {
            log.error("Falha na leitura {}: {}", draftId, e.getClass().getSimpleName());
            drafts.fail(draftId, "Não consegui terminar a leitura. Tente de novo.");
        } finally {
            running.release();
        }
    }

    private ImportQuota quota(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return ImportQuota.of(drafts.sentSince(userId, now.minus(ImportQuota.MONTH)), now);
    }

    private void requireQuota(UUID userId) {
        OffsetDateTime next = quota(userId).nextAt();
        if (next == null) return;
        throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                "Limite de envios atingido. O próximo libera em " + WHEN.format(next.atZoneSameInstant(clock.getZone())) + ".");
    }

    private void requireAttempt(UUID userId) {
        if (!rateLimiter.tryAcquire("import-ai:" + userId, HOURLY_ATTEMPTS, HOUR)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Espere uma hora ou use o seu agente.");
        }
        if (!rateLimiter.tryAcquire(DAILY_KEY, properties.dailyLimit(), DAY)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "As leituras de hoje acabaram. Tente amanhã ou use o seu agente.");
        }
    }

    private static String message(AiException e) {
        return switch (e.failure()) {
            case TIMEOUT -> "A leitura demorou demais. Envie menos documentos de cada vez.";
            case BUSY -> "A IA está sobrecarregada. Tente de novo em alguns minutos.";
            case TOO_LONG -> "São lançamentos demais para uma leitura só. Envie menos documentos de cada vez.";
            case BLOCKED -> "A IA não aceitou ler estes documentos.";
            case REJECTED -> "A IA não conseguiu abrir os documentos. Confira se os arquivos estão inteiros.";
            case FAILED -> "Não consegui falar com a IA. Tente de novo.";
        };
    }
}
