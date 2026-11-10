package com.lifepedia.api.finance.importing;

import com.lifepedia.api.finance.importing.ImportDraft.Origin;
import com.lifepedia.api.finance.importing.ImportDraft.Status;
import com.lifepedia.api.finance.importing.ImportDtos.DraftContent;
import com.lifepedia.api.finance.importing.ImportDtos.DraftRequest;
import com.lifepedia.api.finance.importing.ImportDtos.DraftResponse;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.ai.AiUsage.Outcome;
import com.lifepedia.api.shared.ai.AiUsageLog;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Revisão em aberto da importação: uma por pessoa, até importar ou descartar. */
@Service
public class ImportDraftService {

    private static final List<Status> OPEN = List.of(Status.RUNNING, Status.REVIEW, Status.FAILED);
    private static final int MAX_ITEMS = 500;
    private static final Duration KEEP_REVIEW = Duration.ofDays(30);
    private static final String BUSY = "Você já tem uma importação em andamento. Termine ou descarte a revisão antes.";
    private static final String STOPPED = "A leitura foi interrompida. Tente de novo.";

    private final ImportDraftRepository repository;
    private final NotificationService notifications;
    private final JsonMapper json;
    private final AiUsageLog usage;

    public ImportDraftService(ImportDraftRepository repository, NotificationService notifications, JsonMapper json,
                              AiUsageLog usage) {
        this.repository = repository;
        this.notifications = notifications;
        this.json = json;
        this.usage = usage;
    }

    @Transactional(readOnly = true)
    public Optional<DraftResponse> current(UUID userId) {
        return open(userId).map(DraftResponse::from);
    }

    @Transactional(readOnly = true)
    public DraftContent content(UUID userId, UUID id) {
        ImportDraft draft = find(userId, id);
        return new DraftContent(draft.getResult(), draft.getRows());
    }

    @Transactional(readOnly = true)
    public void requireNoneOpen(UUID userId) {
        if (open(userId).isPresent()) throw new BusinessException(HttpStatus.CONFLICT, BUSY);
    }

    @Transactional(readOnly = true)
    public List<OffsetDateTime> sentSince(UUID userId, OffsetDateTime since) {
        return repository.sentSince(userId, Origin.SITE, Status.FAILED, since);
    }

    /** Grava antes de responder: a leitura segue mesmo sem o navegador. */
    public DraftResponse start(UUID userId, int fileCount) {
        return create(ImportDraft.reading(userId, fileCount));
    }

    public DraftResponse createManual(UUID userId, DraftRequest request) {
        return create(ImportDraft.manual(userId, request.rows(), request.itemCount()));
    }

    @Transactional
    public DraftResponse save(UUID userId, UUID id, DraftRequest request) {
        ImportDraft draft = find(userId, id);
        requireReview(draft);
        draft.updateRows(request.rows(), request.itemCount());
        return DraftResponse.from(draft);
    }

    /** Descarta a revisão ou tira o aviso da leitura que falhou. */
    @Transactional
    public void discard(UUID userId, UUID id) {
        ImportDraft draft = find(userId, id);
        if (draft.getStatus() == Status.RUNNING) {
            throw new BusinessException(HttpStatus.CONFLICT, "Espere a leitura terminar para descartar.");
        }
        end(draft);
    }

    /** Fecha junto com a importação; revisão já fechada não importa de novo. */
    @Transactional
    public void finish(UUID userId, UUID id) {
        ImportDraft draft = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Esta revisão já foi importada ou descartada."));
        requireReview(draft);
        end(draft);
    }

    /** @return resultado para o registro de uso; vazio se a leitura já tinha sido encerrada */
    @Transactional
    public Optional<Completion> complete(UUID id, String text) {
        return repository.findById(id).filter(draft -> draft.getStatus() == Status.RUNNING).map(draft -> {
            int items = itemCount(text);
            if (items == 0) {
                failed(draft, "Não encontrei lançamentos nos documentos.");
                return new Completion(Outcome.EMPTY, 0);
            }
            if (items > MAX_ITEMS) {
                failed(draft, "São " + items + " lançamentos; envie no máximo " + MAX_ITEMS + " de cada vez.");
                return new Completion(Outcome.TOO_MANY, items);
            }
            draft.ready(text, items);
            notifications.notify(draft.getUserId(), NotificationType.IMPORT_READY, null, null,
                    Map.of("draftId", id.toString(), "items", String.valueOf(items)));
            return new Completion(Outcome.SUCCESS, items);
        });
    }

    @Transactional
    public void fail(UUID id, String message) {
        repository.findById(id).filter(draft -> draft.getStatus() == Status.RUNNING).ifPresent(draft -> failed(draft, message));
    }

    /** Leitura perdida (a API reiniciou no meio): sem isso a tela ficaria presa em "lendo". */
    @Transactional
    public void failStale(OffsetDateTime before) {
        repository.findByStatusAndCreatedAtBefore(Status.RUNNING, before).forEach(draft -> {
            failed(draft, STOPPED);
            usage.record(draft.getUserId(), AiUsageLog.IMPORT, Outcome.STOPPED, draft.getFileCount(), 0, 0);
        });
    }

    /** Revisão esquecida perde o conteúdo; a linha fechada sai depois da janela do limite mensal. */
    @Transactional
    public void purge(OffsetDateTime now) {
        repository.findByStatusAndUpdatedAtBefore(Status.REVIEW, now.minus(KEEP_REVIEW)).forEach(this::end);
        repository.findByStatusAndUpdatedAtBefore(Status.FAILED, now.minus(KEEP_REVIEW)).forEach(this::end);
        repository.deleteClosed(Status.CLOSED, now.minus(ImportQuota.MONTH).minusDays(1));
    }

    /** Fim de uma leitura: resultado e lançamentos encontrados. */
    public record Completion(Outcome outcome, int items) {
    }

    private Optional<ImportDraft> open(UUID userId) {
        return repository.findFirstByUserIdAndStatusIn(userId, OPEN);
    }

    private ImportDraft find(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Revisão não encontrada."));
    }

    private static void requireReview(ImportDraft draft) {
        if (draft.getStatus() != Status.REVIEW) {
            throw new BusinessException(HttpStatus.CONFLICT, "Esta revisão já foi importada ou descartada.");
        }
    }

    private DraftResponse create(ImportDraft draft) {
        try {
            return DraftResponse.from(repository.saveAndFlush(draft));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HttpStatus.CONFLICT, BUSY);
        }
    }

    /** Envio à IA segue contando nos limites; o resto não precisa de registro. */
    private void end(ImportDraft draft) {
        if (draft.getOrigin() == Origin.SITE && draft.getStatus() != Status.FAILED) {
            draft.close();
        } else {
            repository.delete(draft);
        }
    }

    private void failed(ImportDraft draft, String message) {
        draft.fail(message);
        notifications.notify(draft.getUserId(), NotificationType.IMPORT_FAILED, null, null,
                Map.of("draftId", draft.getId().toString()));
    }

    private int itemCount(String text) {
        try {
            JsonNode root = json.readTree(text);
            JsonNode items = root.isArray() ? root : root.path("items");
            return items.isArray() ? items.size() : 0;
        } catch (JacksonException e) {
            return 0;
        }
    }
}
