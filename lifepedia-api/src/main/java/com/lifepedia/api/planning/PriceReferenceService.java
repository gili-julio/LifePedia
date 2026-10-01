package com.lifepedia.api.planning;

import com.lifepedia.api.planning.ProductMatcher.Candidate;
import com.lifepedia.api.shared.net.SafeHttpClient;
import com.lifepedia.api.shared.net.SafeHttpClient.FetchException;
import com.lifepedia.api.shared.net.SafeHttpClient.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Acha produto parecido no Buscapé (comparador de preços que libera a busca) para servir de preço/foto de referência. */
@Service
public class PriceReferenceService {

    static final String SITE = "Buscapé";
    private static final String ORIGIN = "https://www.buscape.com.br";
    private static final int MAX_PAGE_BYTES = 3 * 1024 * 1024;
    private static final int MAX_CANDIDATES = 24;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Logger log = LoggerFactory.getLogger(PriceReferenceService.class);

    /** @param url página do produto no Buscapé (ou a busca, para oferta avulsa); @param imageUrls da maior para a menor */
    public record Match(String title, BigDecimal price, String url, List<String> imageUrls, int stores, String store) {
    }

    private final SafeHttpClient http;

    public PriceReferenceService(SafeHttpClient http) {
        this.http = http;
    }

    /** Tenta a busca completa e depois uma mais curta; vazio se nada parecido (ou o site não respondeu). */
    public Optional<Match> find(String name) {
        for (String query : ProductMatcher.queries(name)) {
            URI search = searchUrl(query);
            List<Candidate> candidates;
            try {
                Response page = http.get(search, "text/html", MAX_PAGE_BYTES, type -> type.contains("html"));
                candidates = parse(new String(page.body(), page.charset() == null ? StandardCharsets.UTF_8 : page.charset()));
            } catch (FetchException e) {
                log.debug("Busca de referência falhou: {}", e.getMessage());
                return Optional.empty();
            }
            Candidate best = ProductMatcher.best(name, candidates);
            if (best != null) return Optional.of(toMatch(best, search));
        }
        return Optional.empty();
    }

    /** Produtos e ofertas da página de busca (dados do Next.js em {@code __NEXT_DATA__}). */
    static List<Candidate> parse(String html) {
        Element script = Jsoup.parse(html).getElementById("__NEXT_DATA__");
        if (script == null) return List.of();
        JsonNode hits;
        try {
            hits = JSON.readTree(script.data()).at("/props/initialReduxState/hits/hits");
        } catch (JacksonException e) {
            return List.of();
        }
        List<Candidate> result = new ArrayList<>();
        for (JsonNode hit : hits.values()) {
            if (result.size() >= MAX_CANDIDATES) break;
            String name = text(hit.get("name"));
            BigDecimal price = LinkMetadataParser.parsePrice(text(hit.get("price")));
            if (name == null || price == null) continue;
            boolean offer = !"product".equals(text(hit.get("type")));
            String path = text(hit.get("url"));
            if (path != null && (!path.startsWith("/") || path.startsWith("//"))) path = null;
            JsonNode best = hit.get("bestOffer");
            result.add(new Candidate(name, price, text(hit.get("image")), path, offer, hit.path("storeCount").asInt(0),
                    best == null ? null : text(best.get("merchantName"))));
        }
        return result;
    }

    private static Match toMatch(Candidate c, URI search) {
        String url = !c.offer() && c.path() != null ? ORIGIN + c.path() : search.toString();
        List<String> images = new ArrayList<>();
        if (c.imageUrl() != null) {
            // Miniatura da busca é pequena; "thumbs/12" é a foto grande do mesmo produto.
            String large = c.imageUrl().replaceFirst("/thumbs/\\d+/", "/thumbs/12/");
            if (!large.equals(c.imageUrl())) images.add(large);
            images.add(c.imageUrl());
        }
        return new Match(c.name(), c.price(), url, images, c.stores(), c.store());
    }

    private static URI searchUrl(String query) {
        return URI.create(ORIGIN + "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isObject() || node.isArray()) return null;
        String value = node.asString();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
