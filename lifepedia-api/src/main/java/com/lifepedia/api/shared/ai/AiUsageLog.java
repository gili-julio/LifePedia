package com.lifepedia.api.shared.ai;

import com.lifepedia.api.shared.ai.AiUsage.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Registra cada chamada à IA; falha ao registrar só loga (nunca derruba a leitura). */
@Service
public class AiUsageLog {

    public static final String IMPORT = "IMPORT";
    private static final Logger log = LoggerFactory.getLogger(AiUsageLog.class);

    private final AiUsageRepository repository;

    public AiUsageLog(AiUsageRepository repository) {
        this.repository = repository;
    }

    public void record(UUID userId, String feature, Outcome outcome, int files, int items, long durationMs) {
        try {
            repository.save(new AiUsage(userId, feature, outcome, files, items, durationMs));
        } catch (DataAccessException e) {
            log.warn("Uso da IA não registrado: {}", e.getClass().getSimpleName());
        }
    }
}
