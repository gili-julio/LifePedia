package com.lifepedia.api.price;

import com.lifepedia.api.price.PriceDtos.HistoryPoint;
import com.lifepedia.api.price.PriceDtos.Offer;
import com.lifepedia.api.price.PriceDtos.Stats;
import com.lifepedia.api.price.PricePoint.Source;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PriceBookTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    @Test
    void parsesBuscapeOffersAndHistory() {
        BuscapeProduct product = BuscapeProduct.parse(PriceFixtures.product(new BigDecimal("207.92"))).orElseThrow();
        assertThat(product.name()).isEqualTo("Headphone JBL Tune 520BT");
        assertThat(product.offers()).extracting(BuscapeProduct.Offer::store).containsExactly("KaBuM!", "Amazon");
        assertThat(product.offers().getFirst().totalPrice()).isEqualByComparingTo("259.90");
        assertThat(product.offers().getFirst().url()).isEqualTo("https://www.buscape.com.br/lead?oid=1514100977");
        assertThat(product.offers().get(1).price()).isEqualByComparingTo("224.90");
        assertThat(product.history()).extracting(BuscapeProduct.HistoryDay::store).containsExactly("Amazon", "KaBuM!");
        assertThat(product.storeCount()).isEqualTo(7);
        assertThat(product.imageUrls()).first().isEqualTo("https://image.zst.com.br/foto_235_235.jpg");
        assertThat(BuscapeProduct.parse("<html></html>")).isEmpty();
    }

    @Test
    void mergesOffersWithRecentManualPrices() {
        List<Offer> fetched = List.of(offer("Amazon", "224.90", Source.BUSCAPE), offer("Amazon", "220.00", Source.BUSCAPE),
                offer("Loja", "230.00", Source.LINK));
        List<PricePoint> manual = List.of(point("Feira", "199.90", TODAY.minusDays(2)), point("Velha", "100.00", TODAY.minusDays(30)));
        List<Offer> offers = PriceBook.merge(fetched, manual, TODAY);
        assertThat(offers).extracting(Offer::store).containsExactly("Feira", "Amazon", "Loja");
        assertThat(offers.get(1).price()).isEqualByComparingTo("220.00");
        assertThat(PriceBook.best(offers).source()).isEqualTo(Source.MANUAL);
    }

    @Test
    void computesDailyStatsAndChanges() {
        List<HistoryPoint> daily = PriceBook.daily(List.of(point("A", "300.00", TODAY.minusDays(30)),
                point("B", "250.00", TODAY.minusDays(30)), point("A", "240.00", TODAY.minusDays(7)), point("A", "200.00", TODAY)));
        assertThat(daily).extracting(HistoryPoint::price).map(BigDecimal::toPlainString).containsExactly("250.00", "240.00", "200.00");
        Stats stats = PriceBook.stats(daily, new BigDecimal("200.00"), TODAY);
        assertThat(stats.min()).isEqualByComparingTo("200");
        assertThat(stats.max()).isEqualByComparingTo("250");
        assertThat(stats.avg()).isEqualByComparingTo("230");
        assertThat(stats.change7()).isEqualByComparingTo("-16.7");
        assertThat(stats.change30()).isEqualByComparingTo("-20.0");
    }

    @Test
    void alertsOnTargetAndRelevantDropOnlyOnce() {
        BigDecimal target = new BigDecimal("200");
        assertThat(PriceBook.alert(new BigDecimal("210"), new BigDecimal("215"), target, 10, null)).isNull();
        assertThat(PriceBook.alert(new BigDecimal("199"), new BigDecimal("210"), target, 10, null)).isEqualTo(PriceBook.Alert.TARGET);
        assertThat(PriceBook.alert(new BigDecimal("199"), new BigDecimal("199"), target, 10, new BigDecimal("199"))).isNull();
        assertThat(PriceBook.alert(new BigDecimal("270"), new BigDecimal("300"), null, 10, null)).isEqualTo(PriceBook.Alert.DROP);
        assertThat(PriceBook.alert(new BigDecimal("280"), new BigDecimal("300"), null, 10, null)).isNull();
        assertThat(PriceBook.resetsAlert(new BigDecimal("230"), new BigDecimal("200"), 10)).isTrue();
        assertThat(PriceBook.resetsAlert(new BigDecimal("215"), new BigDecimal("200"), 10)).isFalse();
    }

    private static Offer offer(String store, String price, Source source) {
        return new Offer(store, new BigDecimal(price), null, null, source, TODAY);
    }

    private static PricePoint point(String store, String price, LocalDate day) {
        PricePoint point = new PricePoint(UUID.randomUUID(), day, store, Source.MANUAL, null);
        point.setPrice(new BigDecimal(price), null);
        return point;
    }
}
