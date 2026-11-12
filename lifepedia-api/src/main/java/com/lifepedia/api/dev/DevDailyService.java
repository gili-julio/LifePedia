package com.lifepedia.api.dev;

import com.lifepedia.api.config.AiProperties;
import com.lifepedia.api.dev.DevDtos.Daily;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.shared.ai.AiUsage.Outcome;
import com.lifepedia.api.shared.ai.AiUsageLog;
import com.lifepedia.api.shared.ai.GeminiClient;
import com.lifepedia.api.shared.ai.GeminiClient.AiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Resumo do daily pelo Gemini (ontem, hoje, bloqueios) com os dados do Dev; o prompt é montado aqui, nunca vem do cliente. */
@Service
public class DevDailyService {

    static final int PER_USER_DAY = 10;
    private static final Duration DAY = Duration.ofDays(1);
    private static final String GLOBAL_KEY = "dev-daily:all";
    private static final int MAX_LINES = 8;
    private static final int MAX_LINE = 240;

    private final DevService dev;
    private final GeminiClient gemini;
    private final AiProperties properties;
    private final AiUsageLog usage;
    private final RateLimiter rateLimiter;
    private final JsonMapper json;

    public DevDailyService(DevService dev, GeminiClient gemini, AiProperties properties, AiUsageLog usage, RateLimiter rateLimiter,
                           JsonMapper json) {
        this.dev = dev;
        this.gemini = gemini;
        this.properties = properties;
        this.usage = usage;
        this.rateLimiter = rateLimiter;
        this.json = json;
    }

    public Daily generate(UUID userId) {
        if (!gemini.enabled()) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "A IA não está ligada neste servidor.");
        if (!rateLimiter.tryAcquire("dev-daily:" + userId, PER_USER_DAY, DAY)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Limite de " + PER_USER_DAY + " resumos por dia.");
        }
        if (!rateLimiter.tryAcquire(GLOBAL_KEY, properties.dailyLimit(), DAY)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "A IA chegou ao limite do dia. Tente amanhã.");
        }
        Map<String, Object> facts = dev.dailyFacts(userId);
        long started = System.nanoTime();
        try {
            String answer = gemini.generateJson(DevDailyPrompt.build(json.writeValueAsString(facts)), List.of());
            Daily daily = parse(answer);
            usage.record(userId, AiUsageLog.DEV_DAILY, Outcome.SUCCESS, 0, daily.yesterday().size() + daily.today().size(), ms(started));
            return daily;
        } catch (AiException e) {
            usage.record(userId, AiUsageLog.DEV_DAILY, Outcome.valueOf(e.failure().name()), 0, 0, ms(started));
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "A IA não conseguiu montar o resumo agora. Tente de novo.");
        } catch (JacksonException | IllegalArgumentException e) {
            usage.record(userId, AiUsageLog.DEV_DAILY, Outcome.FAILED, 0, 0, ms(started));
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "A IA respondeu algo inesperado. Tente de novo.");
        }
    }

    /** Lê a resposta (listas de frases curtas) e monta o texto para colar. */
    Daily parse(String answer) {
        JsonNode root = json.readTree(answer);
        List<String> yesterday = lines(root.path("ontem"));
        List<String> today = lines(root.path("hoje"));
        List<String> blockers = lines(root.path("bloqueios"));
        if (yesterday.isEmpty() && today.isEmpty()) throw new IllegalArgumentException("vazio");
        StringBuilder text = new StringBuilder();
        section(text, "Ontem", yesterday);
        section(text, "Hoje", today);
        section(text, "Bloqueios", blockers.isEmpty() ? List.of("Nenhum") : blockers);
        return new Daily(yesterday, today, blockers, text.toString().trim());
    }

    private static List<String> lines(JsonNode array) {
        List<String> lines = new ArrayList<>();
        for (JsonNode item : array) {
            String line = item.asString("").replaceAll("\\s+", " ").trim();
            if (line.isEmpty() || lines.size() >= MAX_LINES) continue;
            lines.add(line.length() <= MAX_LINE ? line : line.substring(0, MAX_LINE));
        }
        return lines;
    }

    private static void section(StringBuilder text, String title, List<String> lines) {
        if (lines.isEmpty()) return;
        text.append(title).append(":\n");
        lines.forEach(l -> text.append("- ").append(l).append('\n'));
        text.append('\n');
    }

    private static long ms(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
