package com.lifepedia.api.delivery;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TrackSnapshotTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void readsStatusEventsCarrierAndForecast() {
        TrackSnapshot s = TrackSnapshot.parse(JSON.readTree("""
                {"latest_status":{"status":"OutForDelivery","sub_status":"OutForDelivery_Other"},
                 "latest_event":{"time_iso":"2026-11-12T08:10:00-03:00","description":"Objeto saiu para entrega ao destinatário",
                                 "location":"NATAL - RN"},
                 "shipping_info":{"shipper_address":{"country":"CN","city":"Shenzhen"}},
                 "time_metrics":{"estimated_delivery_date":{"from":"2026-11-12T00:00:00Z","to":"2026-11-14T00:00:00Z"}},
                 "tracking":{"providers":[{"provider":{"key":2151,"name":"Correios","homepage":"https://www.correios.com.br"},
                   "events":[
                     {"time_iso":"2026-11-05T10:00:00-03:00","description":"Objeto postado","location":"CURITIBA - PR"},
                     {"time_iso":"2026-11-12T08:10:00-03:00","description":"Objeto saiu para entrega ao destinatário",
                      "location":"NATAL - RN"},
                     {"time_iso":"2026-11-08T16:00:00-03:00","description":"Objeto em transferência",
                      "description_translation":{"lang":"pt","description":"Em transferência"},
                      "address":{"city":"Recife","state":"PE","country":"BR"}}]}]}}"""));

        assertThat(s.status()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);
        assertThat(s.status().stage()).isEqualTo(2);
        assertThat(s.carrierName()).isEqualTo("Correios");
        assertThat(s.carrierUrl()).isEqualTo("https://www.correios.com.br");
        assertThat(s.latestAt()).isEqualTo(OffsetDateTime.parse("2026-11-12T08:10:00-03:00"));
        assertThat(s.latestLocation()).isEqualTo("NATAL - RN");
        assertThat(s.origin()).isEqualTo("Shenzhen, CN");
        assertThat(s.estimatedFrom()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(s.estimatedTo()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(s.events()).extracting(TrackSnapshot.TrackEvent::text)
                .containsExactly("Objeto saiu para entrega ao destinatário", "Em transferência", "Objeto postado");
        assertThat(s.events().get(1).location()).isEqualTo("Recife, PE, BR");
        assertThat(s.taxPending()).isFalse();
    }

    @Test
    void emptyInfoIsPendingAndUnsafeHomepageIsDropped() {
        TrackSnapshot s = TrackSnapshot.parse(JSON.readTree("""
                {"latest_status":{"status":"NotFound"},
                 "tracking":{"providers":[{"provider":{"name":"X","homepage":"javascript:alert(1)"},"events":[]}]}}"""));
        assertThat(s.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(s.status().stage()).isEqualTo(-1);
        assertThat(s.carrierUrl()).isNull();
        assertThat(s.latestAt()).isNull();
        assertThat(s.events()).isEmpty();
    }

    @Test
    void detectsImportTax() {
        assertThat(TrackSnapshot.taxPending("Aguardando pagamento do despacho postal")).isTrue();
        assertThat(TrackSnapshot.taxPending("Objeto tributado")).isTrue();
        assertThat(TrackSnapshot.taxPending("Awaiting payment of customs duty")).isTrue();
        assertThat(TrackSnapshot.taxPending("Pagamento confirmado")).isFalse();
        assertThat(TrackSnapshot.taxPending("Objeto em trânsito")).isFalse();
    }

    @Test
    void mapsEveryStatus() {
        assertThat(DeliveryStatus.of("InfoReceived")).isEqualTo(DeliveryStatus.POSTED);
        assertThat(DeliveryStatus.of("AvailableForPickup")).isEqualTo(DeliveryStatus.PICKUP);
        assertThat(DeliveryStatus.of("Delivered").stage()).isEqualTo(3);
        assertThat(DeliveryStatus.of("DeliveryFailure").problem()).isTrue();
        assertThat(DeliveryStatus.of("Expired").problem()).isTrue();
        assertThat(DeliveryStatus.of("Algo novo")).isEqualTo(DeliveryStatus.PENDING);
    }
}
