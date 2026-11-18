package com.lifepedia.api.price;

import com.lifepedia.api.planning.LinkMetadataParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Página de produto do Buscapé lida dos dados do Next.js ({@code __NEXT_DATA__}): ofertas por loja (a mais barata de
 * cada uma) e o histórico do menor preço por dia (pura, sem rede).
 *
 * @param imageUrls da maior para a menor
 */
public record BuscapeProduct(String name, List<String> imageUrls, List<Offer> offers, List<HistoryDay> history,
                             int storeCount) {

    static final String ORIGIN = "https://www.buscape.com.br";
    static final String UNKNOWN_STORE = "Buscapé";
    private static final int MAX_OFFERS = 30;
    private static final int MAX_HISTORY = 400;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * @param price      à vista
     * @param totalPrice parcelado (nulo se igual)
     * @param url        link do Buscapé que leva à loja
     */
    public record Offer(String store, BigDecimal price, BigDecimal totalPrice, String url) {
    }

    public record HistoryDay(LocalDate day, BigDecimal price, String store) {
    }

    public static Optional<BuscapeProduct> parse(String html) {
        Document doc = Jsoup.parse(html);
        Element script = doc.getElementById("__NEXT_DATA__");
        if (script == null) return Optional.empty();
        JsonNode state;
        try {
            state = JSON.readTree(script.data()).at("/props/initialReduxState");
        } catch (JacksonException e) {
            return Optional.empty();
        }
        Map<String, Offer> byStore = new LinkedHashMap<>();
        Map<String, String> storeById = new HashMap<>();
        List<String> images = new ArrayList<>();
        meta(doc, "og:image").ifPresent(images::add);
        for (JsonNode o : state.at("/offers/offerList").values()) {
            String store = text(o.get("sellerName"));
            BigDecimal price = price(o.get("price"));
            if (store == null || price == null) continue;
            String merchant = text(o.get("merchantId"));
            if (merchant != null) storeById.putIfAbsent(merchant, store);
            BigDecimal total = price(o.get("totalPrice"));
            String id = text(o.get("id"));
            Offer offer = new Offer(limit(store), price, total == null || total.compareTo(price) <= 0 ? null : total,
                    id == null || !id.matches("\\d{1,20}") ? null : ORIGIN + "/lead?oid=" + id);
            byStore.merge(offer.store(), offer, (a, b) -> b.price().compareTo(a.price()) < 0 ? b : a);
            String image = text(o.get("imageUrl"));
            if (image != null && image.startsWith("https://") && images.size() < 3) images.add(image);
        }
        List<HistoryDay> history = new ArrayList<>();
        for (JsonNode product : state.at("/priceHistory/priceHistory").values()) {
            for (JsonNode d : product.path("days").values()) {
                if (history.size() >= MAX_HISTORY) break;
                LocalDate day = date(text(d.get("date")));
                BigDecimal price = price(d.get("price"));
                if (day == null || price == null) continue;
                history.add(new HistoryDay(day, price, storeById.getOrDefault(text(d.get("merchantId")), UNKNOWN_STORE)));
            }
        }
        String name = text(state.at("/products/product/product/name"));
        if (name == null && byStore.isEmpty()) return Optional.empty();
        List<Offer> offers = byStore.values().stream()
                .sorted((a, b) -> a.price().compareTo(b.price())).limit(MAX_OFFERS).toList();
        int stores = state.at("/offers/countOfStores").asInt(offers.size());
        return Optional.of(new BuscapeProduct(name == null ? null : limit(name, 160), List.copyOf(images), offers,
                List.copyOf(history), Math.max(stores, offers.size())));
    }

    private static Optional<String> meta(Document doc, String property) {
        Element tag = doc.selectFirst("meta[property=" + property + "]");
        String value = tag == null ? null : tag.attr("content").trim();
        return value == null || !value.startsWith("https://") ? Optional.empty() : Optional.of(value);
    }

    private static BigDecimal price(JsonNode node) {
        if (node == null || node.isNull()) return null;
        BigDecimal value = node.isNumber() ? node.decimalValue() : LinkMetadataParser.parsePrice(text(node));
        return value == null || value.signum() <= 0 ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate date(String value) {
        if (value == null || value.length() < 10) return null;
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isObject() || node.isArray()) return null;
        String value = node.asString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String limit(String value) {
        return limit(value, 80);
    }

    private static String limit(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
