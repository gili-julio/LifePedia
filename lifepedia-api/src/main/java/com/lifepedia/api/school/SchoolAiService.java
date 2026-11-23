package com.lifepedia.api.school;

import com.lifepedia.api.config.AiProperties;
import com.lifepedia.api.school.SchoolCourse.Category;
import com.lifepedia.api.school.SchoolCourse.Status;
import com.lifepedia.api.school.SchoolDtos.AiImport;
import com.lifepedia.api.school.SchoolDtos.Hours;
import com.lifepedia.api.school.SchoolDtos.ImportCourse;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.shared.ai.AiUsage.Outcome;
import com.lifepedia.api.shared.ai.AiUsageLog;
import com.lifepedia.api.shared.ai.GeminiClient;
import com.lifepedia.api.shared.ai.GeminiClient.AiException;
import com.lifepedia.api.shared.ai.GeminiClient.Attachment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Leitura do histórico (PDF ou foto, qualquer instituição) pelo Gemini; nada é guardado, a pessoa revisa antes de importar. */
@Service
public class SchoolAiService {

    static final int PER_USER_DAY = 5;
    static final int MAX_FILES = 3;
    static final long MAX_BYTES = 15L * 1024 * 1024;
    private static final int MAX_COURSES = 200;
    private static final Duration DAY = Duration.ofDays(1);
    private static final String GLOBAL_KEY = "school-import:all";

    private final GeminiClient gemini;
    private final AiProperties properties;
    private final AiUsageLog usage;
    private final RateLimiter rateLimiter;
    private final JsonMapper json;

    public SchoolAiService(GeminiClient gemini, AiProperties properties, AiUsageLog usage, RateLimiter rateLimiter, JsonMapper json) {
        this.gemini = gemini;
        this.properties = properties;
        this.usage = usage;
        this.rateLimiter = rateLimiter;
        this.json = json;
    }

    public AiImport read(UUID userId, List<MultipartFile> files) {
        if (!gemini.enabled()) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "A IA não está ligada neste servidor.");
        List<Attachment> attachments = attachments(files);
        if (!rateLimiter.tryAcquire("school-import:" + userId, PER_USER_DAY, DAY)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Limite de " + PER_USER_DAY + " leituras por dia.");
        }
        if (!rateLimiter.tryAcquire(GLOBAL_KEY, properties.dailyLimit(), DAY)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "A IA chegou ao limite do dia. Tente amanhã.");
        }
        long started = System.nanoTime();
        try {
            AiImport result = parse(gemini.generateJson(SchoolImportPrompt.TEXT, attachments));
            usage.record(userId, AiUsageLog.SCHOOL_IMPORT, Outcome.SUCCESS, attachments.size(), result.courses().size(), ms(started));
            return result;
        } catch (AiException e) {
            usage.record(userId, AiUsageLog.SCHOOL_IMPORT, Outcome.valueOf(e.failure().name()), attachments.size(), 0, ms(started));
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "A IA não conseguiu ler o histórico agora. Tente de novo.");
        } catch (JacksonException | IllegalArgumentException e) {
            usage.record(userId, AiUsageLog.SCHOOL_IMPORT, Outcome.FAILED, attachments.size(), 0, ms(started));
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Não encontrei matérias no arquivo. Confira se é o histórico.");
        }
    }

    /** Lê a resposta, descartando o que não for válido. */
    AiImport parse(String answer) {
        JsonNode root = json.readTree(answer);
        List<ImportCourse> courses = new ArrayList<>();
        for (JsonNode item : root.path("materias")) {
            String name = text(item, "nome", 200);
            if (name == null) continue;
            courses.add(new ImportCourse(period(text(item, "periodo", 20)), text(item, "codigo", 20), name,
                    hours(item.path("ch")), text(item, "turma", 20), category(text(item, "tipo", 20)), status(text(item, "situacao", 10)),
                    decimal(item.path("frequencia"), 100), decimal(item.path("media"), 10), concept(text(item, "conceito", 2)),
                    text(item, "professor", 160)));
            if (courses.size() >= MAX_COURSES) break;
        }
        if (courses.isEmpty()) throw new IllegalArgumentException("sem matérias");
        JsonNode hours = root.path("cargaExigida");
        return new AiImport(text(root, "curso", 160), text(root, "instituicao", 160),
                new Hours(hours(hours.path("obrigatorias")), hours(hours.path("optativas")), hours(hours.path("complementares"))), courses);
    }

    private static List<Attachment> attachments(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha o PDF ou a foto do histórico.");
        if (files.size() > MAX_FILES) throw new BusinessException(HttpStatus.BAD_REQUEST, "Até " + MAX_FILES + " arquivos.");
        long total = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (total > MAX_BYTES) throw new BusinessException(HttpStatus.BAD_REQUEST, "Os arquivos passam de 15 MB.");
        List<Attachment> list = new ArrayList<>();
        for (MultipartFile file : files) {
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException e) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo.");
            }
            String type = type(bytes);
            if (type == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Envie PDF, PNG, JPG ou WebP.");
            list.add(new Attachment(type, bytes));
        }
        return list;
    }

    /** Tipo pelo conteúdo (nunca pelo nome ou cabeçalho do envio). */
    private static String type(byte[] b) {
        if (b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "application/pdf";
        if (b.length > 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length > 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8) return "image/jpeg";
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B'
                && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String text(JsonNode node, String field, int max) {
        JsonNode value = node.path(field);
        if (!value.isString()) return null;
        String text = value.asString().trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) return null;
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String period(String value) {
        if (value == null) return null;
        String clean = value.replace('/', '.').replace('-', '.');
        return clean.matches("\\d{4}\\.[1-4]") ? clean : null;
    }

    private static Integer hours(JsonNode node) {
        if (!node.isNumber() && !node.isString()) return null;
        try {
            int value = node.isNumber() ? node.asInt() : Integer.parseInt(node.asString().replaceAll("\\D", ""));
            return value >= 0 && value <= 20000 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode node, int max) {
        if (!node.isNumber() && !node.isString()) return null;
        try {
            BigDecimal value = node.isNumber() ? node.decimalValue() : new BigDecimal(node.asString().trim().replace(',', '.'));
            return value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(max)) <= 0 ? value.setScale(2, RoundingMode.HALF_UP) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String concept(String value) {
        return value != null && value.matches("(?i)[A-E]") ? value.toUpperCase(Locale.ROOT) : null;
    }

    private static Category category(String value) {
        if (value == null) return Category.MANDATORY;
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "OPTATIVA" -> Category.OPTIONAL;
            case "ELETIVA" -> Category.ELECTIVE;
            case "COMPLEMENTAR" -> Category.COMPLEMENTARY;
            default -> Category.MANDATORY;
        };
    }

    private static Status status(String value) {
        if (value == null) return Status.MATR;
        try {
            return Status.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Status.MATR;
        }
    }

    private static long ms(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
