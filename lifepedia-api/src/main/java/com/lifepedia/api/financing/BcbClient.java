package com.lifepedia.api.financing;

import com.lifepedia.api.shared.net.SafeHttpClient;
import com.lifepedia.api.shared.net.SafeHttpClient.FetchException;
import com.lifepedia.api.shared.net.SafeHttpClient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Dados abertos do Banco Central: taxas de juros por banco (Olinda) e índices de correção (SGS). Falha = vazio. */
@Component
public class BcbClient {

    private static final String RATES = "https://olinda.bcb.gov.br/olinda/servico/taxaJuros/versao/v2/odata/";
    private static final String SGS = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.%d/dados/ultimos/%d?formato=json";
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final DateTimeFormatter SGS_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Logger log = LoggerFactory.getLogger(BcbClient.class);

    /** @param monthly e @param yearly em % */
    public record BankRate(int position, String bank, BigDecimal monthly, BigDecimal yearly) {
    }

    public record Published(LocalDate period, List<BankRate> banks) {
    }

    public record IndexPoint(YearMonth month, BigDecimal rate) {
    }

    private final SafeHttpClient http;
    private final Clock clock;

    public BcbClient(SafeHttpClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    /**
     * Última divulgação com dados da modalidade (pessoa física). Busca as divulgações recentes num intervalo de datas
     * (ordenar no servidor do Banco Central leva mais de 10 s) e fica com a mais nova.
     */
    public Optional<Published> rates(Modality modality) {
        LocalDate since = "M".equals(modality.period()) ? LocalDate.now(clock).minusMonths(6) : LocalDate.now(clock).minusDays(45);
        try {
            JsonNode rows = get(RATES + "ConsultaUnificada?$format=json&$top=5000&$filter=" + encode(
                    "InicioPeriodo ge '" + since + "' and codigoModalidade eq '" + modality.code() + "' and codigoSegmento eq '1'"));
            Map<String, List<BankRate>> byPeriod = new TreeMap<>(Comparator.reverseOrder());
            for (JsonNode row : rows.path("value").values()) {
                String start = row.path("InicioPeriodo").asString();
                String bank = row.path("InstituicaoFinanceira").asString();
                BigDecimal monthly = decimal(row.get("TaxaJurosAoMes"));
                BigDecimal yearly = decimal(row.get("TaxaJurosAoAno"));
                if (start == null || bank == null || bank.isBlank() || monthly == null || yearly == null) continue;
                List<BankRate> banks = byPeriod.computeIfAbsent(start, k -> new ArrayList<>());
                banks.add(new BankRate(row.path("Posicao").asInt(banks.size() + 1), trim(bank, 160), monthly, yearly));
            }
            return byPeriod.entrySet().stream().findFirst().map(e -> new Published(LocalDate.parse(e.getKey()),
                    e.getValue().stream().sorted(Comparator.comparingInt(BankRate::position)).toList()));
        } catch (FetchException | RuntimeException e) {
            log.warn("Taxas do Banco Central indisponíveis ({}): {}", modality, e.getMessage());
        }
        return Optional.empty();
    }

    /** Últimos {@code months} valores mensais do índice. */
    public List<IndexPoint> index(CorrectionIndex index, int months) {
        if (index == CorrectionIndex.NONE) return List.of();
        try {
            List<IndexPoint> points = new ArrayList<>();
            for (JsonNode row : get(SGS.formatted(index.series(), months)).values()) {
                BigDecimal rate = decimal(row.get("valor"));
                String date = row.path("data").asString();
                if (rate == null || date == null) continue;
                points.add(new IndexPoint(YearMonth.from(LocalDate.parse(date, SGS_DATE)), rate));
            }
            return points;
        } catch (FetchException | RuntimeException e) {
            log.warn("Índice {} indisponível: {}", index, e.getMessage());
            return List.of();
        }
    }

    private JsonNode get(String url) throws FetchException {
        Response response = http.get(URI.create(url), "application/json", MAX_BYTES, type -> type.contains("json"));
        return JSON.readTree(new String(response.body(),
                response.charset() == null ? StandardCharsets.UTF_8 : response.charset()));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            return new BigDecimal(node.asString().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(String value, int max) {
        String text = value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
