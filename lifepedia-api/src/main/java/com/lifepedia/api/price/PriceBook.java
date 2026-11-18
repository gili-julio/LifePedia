package com.lifepedia.api.price;

import com.lifepedia.api.price.PriceDtos.HistoryPoint;
import com.lifepedia.api.price.PriceDtos.Offer;
import com.lifepedia.api.price.PriceDtos.Stats;
import com.lifepedia.api.price.PricePoint.Source;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Contas do acompanhamento de preço (puras): ofertas, menor preço, histórico por dia, estatísticas e avisos. */
final class PriceBook {

    /** Preço anotado à mão vale como oferta por esses dias. */
    static final int MANUAL_DAYS = 7;
    static final int STATS_DAYS = 90;
    static final int HISTORY_DAYS = 180;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    enum Alert { TARGET, DROP }

    private PriceBook() {
    }

    /** Ofertas consultadas + preços anotados à mão recentes, uma por loja e origem, da mais barata à mais cara. */
    static List<Offer> merge(List<Offer> fetched, List<PricePoint> manual, LocalDate today) {
        Map<String, Offer> byKey = new LinkedHashMap<>();
        for (Offer offer : fetched) {
            if (offer.source() != Source.MANUAL) byKey.merge(offer.source() + "|" + offer.store(), offer, PriceBook::cheaper);
        }
        for (PricePoint p : manual) {
            if (p.getDay().isBefore(today.minusDays(MANUAL_DAYS))) continue;
            Offer offer = new Offer(p.getStore(), p.getPrice(), null, p.getUrl(), Source.MANUAL, p.getDay());
            byKey.merge(Source.MANUAL + "|" + p.getStore(), offer, (a, b) -> a.day().isAfter(b.day()) ? a : b);
        }
        List<Offer> offers = new ArrayList<>(byKey.values());
        offers.sort(Comparator.comparing(Offer::price));
        return offers;
    }

    static Offer best(List<Offer> offers) {
        return offers.isEmpty() ? null : offers.getFirst();
    }

    /** Menor preço de cada dia (de qualquer loja e origem), do mais antigo ao mais novo. */
    static List<HistoryPoint> daily(List<PricePoint> points) {
        Map<LocalDate, HistoryPoint> byDay = new TreeMap<>();
        for (PricePoint p : points) {
            HistoryPoint point = new HistoryPoint(p.getDay(), p.getPrice(), p.getStore());
            byDay.merge(p.getDay(), point, (a, b) -> b.price().compareTo(a.price()) < 0 ? b : a);
        }
        return List.copyOf(byDay.values());
    }

    static Stats stats(List<HistoryPoint> daily, BigDecimal current, LocalDate today) {
        List<HistoryPoint> window = daily.stream().filter(p -> !p.day().isBefore(today.minusDays(STATS_DAYS))).toList();
        if (window.isEmpty()) return new Stats(null, null, null, 0, null, null);
        BigDecimal min = window.stream().map(HistoryPoint::price).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = window.stream().map(HistoryPoint::price).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal sum = window.stream().map(HistoryPoint::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(window.size()), 2, RoundingMode.HALF_UP);
        return new Stats(min, avg, max, window.size(), change(daily, current, today.minusDays(7)),
                change(daily, current, today.minusDays(30)));
    }

    /**
     * Aviso desta consulta, se houver: chegou ao alvo (e está abaixo do último aviso) ou caiu pelo menos
     * {@code dropPercent} desde a consulta anterior.
     */
    static Alert alert(BigDecimal best, BigDecimal previous, BigDecimal target, int dropPercent, BigDecimal alerted) {
        if (best == null || (alerted != null && best.compareTo(alerted) >= 0)) return null;
        if (target != null && best.compareTo(target) <= 0) return Alert.TARGET;
        if (previous != null && best.compareTo(lessBy(previous, dropPercent)) <= 0) return Alert.DROP;
        return null;
    }

    /** Depois de subir {@code dropPercent} acima do último aviso, a próxima queda avisa de novo. */
    static boolean resetsAlert(BigDecimal best, BigDecimal alerted, int dropPercent) {
        return best != null && alerted != null
                && best.compareTo(alerted.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(dropPercent).divide(HUNDRED)))) > 0;
    }

    static BigDecimal percent(BigDecimal now, BigDecimal before) {
        if (now == null || before == null || before.signum() == 0) return null;
        return now.subtract(before).multiply(HUNDRED).divide(before, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal change(List<HistoryPoint> daily, BigDecimal current, LocalDate day) {
        HistoryPoint past = null;
        for (HistoryPoint p : daily) {
            if (p.day().isAfter(day)) break;
            past = p;
        }
        return past == null ? null : percent(current, past.price());
    }

    private static BigDecimal lessBy(BigDecimal value, int percent) {
        return value.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(percent).divide(HUNDRED)));
    }

    private static Offer cheaper(Offer a, Offer b) {
        return b.price().compareTo(a.price()) < 0 ? b : a;
    }
}
