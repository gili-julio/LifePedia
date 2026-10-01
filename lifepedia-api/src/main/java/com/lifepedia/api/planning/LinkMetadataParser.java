package com.lifepedia.api.planning;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extrai nome, preço, imagem e loja de uma página de produto: JSON-LD, Open Graph, microdados e Amazon. */
final class LinkMetadataParser {

    /** @param structured veio de dados do produto (não só do {@code <title>}) */
    record Metadata(String title, BigDecimal price, String currency, String imageUrl, String site, boolean structured) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern AMAZON_PRICE = Pattern.compile("\"priceAmount\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern ID_TOKEN = Pattern.compile(
            "^(\\d{5,}|mlb\\d*|mla\\d*|jm|p|dp|i\\.\\d+(\\.\\d+)*|[a-z]*\\d[a-z\\d]{7,})$");
    private static final Set<String> TARGET_PARAMS = Set.of("go", "url", "redirect", "redirect_url",
            "return_to", "returnurl", "next", "continue");
    private static final Pattern GATE_PATH = Pattern.compile("verif|login|signin|captcha|challenge|blocked|security");
    private static final Map<String, String> SITES = Map.of(
            "mercadolivre", "Mercado Livre",
            "amazon", "Amazon",
            "shopee", "Shopee",
            "magazineluiza", "Magalu",
            "kabum", "KaBuM!",
            "aliexpress", "AliExpress",
            "americanas", "Americanas",
            "casasbahia", "Casas Bahia");

    private LinkMetadataParser() {
    }

    static Metadata parse(Document doc, URI url) {
        Map<String, String> meta = metaTags(doc);
        JsonNode product = jsonLdProduct(doc);

        String ldName = product == null ? null : text(product.get("name"));
        String title = first(ldName, meta.get("og:title"), meta.get("twitter:title"), ownText(doc, "#productTitle"));
        boolean structured = title != null;
        if (title == null) title = cleanTitle(doc.title(), url);

        BigDecimal price = product == null ? null : offerPrice(product.get("offers"));
        if (price == null) price = parsePrice(first(meta.get("product:price:amount"), meta.get("og:price:amount"), meta.get("price")));
        if (price == null) price = amazonPrice(doc);
        String currency = product == null ? null : offerCurrency(product.get("offers"));
        if (currency == null) currency = first(meta.get("product:price:currency"), meta.get("og:price:currency"), meta.get("pricecurrency"));

        String image = first(product == null ? null : imageOf(product.get("image")), meta.get("og:image:secure_url"),
                meta.get("og:image"), meta.get("twitter:image"), amazonImage(doc), linkHref(doc, "link[rel=image_src]"));
        String imageUrl = absolute(url, image);

        structured |= price != null || imageUrl != null;
        return new Metadata(limit(title, 300), price, currency == null ? null : currency.trim().toUpperCase(Locale.ROOT),
                imageUrl, first(limit(meta.get("og:site_name"), 120), siteName(url)), structured);
    }

    /** Nome legível tirado do endereço (ex.: {@code /apple-iphone-15-preto/p/MLB123} → "Apple iphone 15 preto"). */
    static String titleFromUrl(URI url) {
        String path = url.getRawPath() == null ? "" : url.getRawPath();
        String best = null;
        int bestWords = 1;
        for (String segment : path.split("/")) {
            String decoded = URLDecoder.decode(segment, StandardCharsets.UTF_8);
            List<String> words = new ArrayList<>();
            for (String token : decoded.split("[-_+\\s]+")) {
                if (!token.isBlank() && !ID_TOKEN.matcher(token.toLowerCase(Locale.ROOT)).matches()) words.add(token);
            }
            if (words.size() > bestWords && words.stream().anyMatch(w -> w.chars().anyMatch(Character::isLetter))) {
                best = String.join(" ", words);
                bestWords = words.size();
            }
        }
        if (best == null) return null;
        return limit(Character.toUpperCase(best.charAt(0)) + best.substring(1), 120);
    }

    /** Nome do endereço final de um redirecionamento; em página de verificação/login, do destino em {@code go}/{@code url}. */
    static String titleFromRedirect(URI finalUrl) {
        String query = finalUrl.getRawQuery() == null ? "" : finalUrl.getRawQuery();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0 || !TARGET_PARAMS.contains(pair.substring(0, eq).toLowerCase(Locale.ROOT))) continue;
            try {
                URI target = URI.create(URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                if (target.getScheme() != null && target.getScheme().startsWith("http")) {
                    String title = titleFromUrl(target);
                    if (title != null) return title;
                }
            } catch (IllegalArgumentException ignored) {
                // Parâmetro que não é endereço.
            }
        }
        String path = finalUrl.getRawPath() == null ? "" : finalUrl.getRawPath().toLowerCase(Locale.ROOT);
        return GATE_PATH.matcher(path).find() ? null : titleFromUrl(finalUrl);
    }

    /** "Mercado Livre", "Amazon"... ou o domínio sem {@code www.}. */
    static String siteName(URI url) {
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        host = host.replaceFirst("^(www|m)\\.", "");
        for (String label : host.split("\\.")) {
            String known = SITES.get(label);
            if (known != null) return known;
        }
        return limit(host, 120);
    }

    /** Aceita "4510.89", "4.510,89", "R$ 4.776,77" e "1,234.56". */
    static BigDecimal parsePrice(String raw) {
        if (raw == null) return null;
        String value = raw.replaceAll("[^0-9.,]", "");
        if (value.isEmpty()) return null;
        int comma = value.lastIndexOf(',');
        int dot = value.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            value = comma > dot ? value.replace(".", "").replace(',', '.') : value.replace(",", "");
        } else if (comma >= 0) {
            value = value.matches("\\d{1,3}(,\\d{3})+") ? value.replace(",", "") : value.replace(',', '.');
        } else if (value.matches("\\d{1,3}(\\.\\d{3})+")) {
            value = value.replace(".", "");
        }
        try {
            BigDecimal price = new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
            return price.signum() > 0 && price.compareTo(new BigDecimal("999999999999")) < 0 ? price : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, String> metaTags(Document doc) {
        Map<String, String> tags = new HashMap<>();
        for (Element el : doc.select("meta[content]")) {
            String key = first(el.attr("property"), el.attr("name"), el.attr("itemprop"));
            String content = el.attr("content").trim();
            if (key != null && !content.isEmpty()) tags.putIfAbsent(key.toLowerCase(Locale.ROOT), content);
        }
        return tags;
    }

    private static JsonNode jsonLdProduct(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode found = findProduct(JSON.readTree(script.data()), 0);
                if (found != null) return found;
            } catch (JacksonException ignored) {
                // JSON-LD inválido: tenta o próximo.
            }
        }
        return null;
    }

    private static JsonNode findProduct(JsonNode node, int depth) {
        if (node == null || depth > 6) return null;
        if (node.isArray()) {
            for (JsonNode child : node.values()) {
                JsonNode found = findProduct(child, depth + 1);
                if (found != null) return found;
            }
            return null;
        }
        if (!node.isObject()) return null;
        JsonNode type = node.get("@type");
        if (type != null && (type.isArray() ? type.values().stream().anyMatch(t -> isProduct(t.asString())) : isProduct(type.asString()))) {
            return node;
        }
        return findProduct(node.get("@graph"), depth + 1);
    }

    private static boolean isProduct(String type) {
        return type != null && (type.equalsIgnoreCase("Product") || type.equalsIgnoreCase("ProductGroup"));
    }

    private static BigDecimal offerPrice(JsonNode offers) {
        if (offers == null) return null;
        if (offers.isArray()) {
            for (JsonNode offer : offers.values()) {
                BigDecimal price = offerPrice(offer);
                if (price != null) return price;
            }
            return null;
        }
        BigDecimal price = parsePrice(text(offers.get("price")));
        if (price == null) price = parsePrice(text(offers.get("lowPrice")));
        return price;
    }

    private static String offerCurrency(JsonNode offers) {
        if (offers == null) return null;
        if (offers.isArray()) return offers.values().stream().map(LinkMetadataParser::offerCurrency).filter(c -> c != null).findFirst().orElse(null);
        return text(offers.get("priceCurrency"));
    }

    private static String imageOf(JsonNode image) {
        if (image == null) return null;
        if (image.isArray()) return image.values().stream().map(LinkMetadataParser::imageOf).filter(i -> i != null).findFirst().orElse(null);
        if (image.isObject()) return text(image.get("url"));
        return text(image);
    }

    private static BigDecimal amazonPrice(Document doc) {
        Matcher m = AMAZON_PRICE.matcher(doc.html());
        if (m.find()) return parsePrice(m.group(1));
        Element offscreen = doc.selectFirst(".a-price .a-offscreen");
        return offscreen == null ? null : parsePrice(offscreen.text());
    }

    private static String amazonImage(Document doc) {
        Element landing = doc.selectFirst("#landingImage");
        if (landing == null) return null;
        String hires = landing.attr("data-old-hires");
        if (!hires.isBlank()) return hires;
        String dynamic = landing.attr("data-a-dynamic-image");
        try {
            JsonNode images = JSON.readTree(dynamic);
            return images.isObject() ? images.properties().stream().map(Map.Entry::getKey).findFirst().orElse(null) : null;
        } catch (JacksonException e) {
            return null;
        }
    }

    /** Tira o sufixo com o nome da loja ("Produto | Amazon.com.br"). */
    private static String cleanTitle(String title, URI url) {
        if (title == null || title.isBlank()) return null;
        String root = url.getHost() == null ? "" : url.getHost().replaceFirst("^(www|m)\\.", "").split("\\.")[0];
        String[] parts = title.split("\\s+[|–—-]\\s+");
        if (parts.length > 1 && !root.isEmpty() && parts[parts.length - 1].toLowerCase(Locale.ROOT).contains(root)) {
            return String.join(" - ", java.util.Arrays.copyOf(parts, parts.length - 1)).trim();
        }
        return title.trim();
    }

    private static String ownText(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        return el == null ? null : el.text();
    }

    private static String linkHref(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        return el == null ? null : el.attr("href");
    }

    private static String absolute(URI base, String href) {
        if (href == null) return null;
        try {
            URI resolved = base.resolve(href.trim().replace(" ", "%20"));
            String scheme = resolved.getScheme();
            return scheme != null && (scheme.equals("http") || scheme.equals("https")) ? resolved.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isObject() || node.isArray()) return null;
        String value = node.asString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String first(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }

    private static String limit(String value, int max) {
        if (value == null) return null;
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max).trim();
    }
}
