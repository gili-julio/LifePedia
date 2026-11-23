package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.delivery.CepClient;
import com.lifepedia.api.delivery.DeliveryRepository;
import com.lifepedia.api.delivery.DeliverySyncService;
import com.lifepedia.api.delivery.Track17Client;
import com.lifepedia.api.delivery.Track17Client.Registered;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DeliveryIntegrationTest extends ApiTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @MockitoBean
    Track17Client track17;

    @MockitoBean
    CepClient cep;

    @Autowired
    DeliverySyncService sync;

    @Autowired
    DeliveryRepository deliveries;

    private final Map<String, JsonNode> remote = new ConcurrentHashMap<>();

    @BeforeEach
    void mockTracking() {
        when(track17.enabled()).thenReturn(true);
        try {
            when(track17.register(anyMap()))
                    .thenAnswer(call -> new Registered(new HashSet<>(call.<Map<String, Integer>>getArgument(0).keySet()), Map.of()));
            when(track17.changeCarrier(anyMap()))
                    .thenAnswer(call -> new Registered(new HashSet<>(call.<Map<String, Integer>>getArgument(0).keySet()), Map.of()));
            when(track17.track(anyList())).thenAnswer(call -> {
                Map<String, JsonNode> result = new HashMap<>();
                for (String code : call.<List<String>>getArgument(0)) if (remote.containsKey(code)) result.put(code, remote.get(code));
                return result;
            });
        } catch (Track17Client.TrackException e) {
            throw new IllegalStateException(e);
        }
        when(cep.find(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void tracksADeliveryWithAddressAndNotifiesChanges() {
        String bearer = registerAndGetBearer();
        String code = code();
        remote.put(code, info("InTransit", "Objeto em transferência", at(3, 10)));
        when(cep.find("01001000")).thenReturn(Optional.of(new CepClient.Address("01001000", "Rua A", "Centro", "São Paulo", "SP")));

        assertThat(send("GET", bearer, "/api/deliveries/cep/01001-000", null)).bodyJson()
                .extractingPath("$.city").isEqualTo("São Paulo");
        assertThat(send("GET", bearer, "/api/deliveries/cep/12345678", null)).hasStatus(404);
        String home = JsonPath.read(post(bearer, "/api/deliveries/addresses", """
                {"label":"Casa","cep":"01001-000","street":"Rua A","number":"10","city":"São Paulo","state":"sp"}"""), "$.id");
        String work = JsonPath.read(post(bearer, "/api/deliveries/addresses", """
                {"label":"Trabalho","cep":"01001001","street":"Rua B","city":"São Paulo","state":"SP","defaultAddress":true}"""), "$.id");
        assertThat(send("GET", bearer, "/api/deliveries/addresses", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$[0].id").isEqualTo(work);
            assertThat(json).extractingPath("$[1].defaultAddress").isEqualTo(false);
            assertThat(json).extractingPath("$[1].state").isEqualTo("SP");
        });

        String created = post(bearer, "/api/deliveries", """
                {"trackingCode":" %s ","name":"Fone","addressId":"%s"}""".formatted(code.toLowerCase(), home));
        String id = JsonPath.read(created, "$.id");
        assertThat(JsonPath.<String>read(created, "$.status")).isEqualTo("IN_TRANSIT");
        assertThat(JsonPath.<Integer>read(created, "$.stage")).isEqualTo(1);
        assertThat(JsonPath.<String>read(created, "$.trackingCode")).isEqualTo(code);
        assertThat(JsonPath.<String>read(created, "$.address.label")).isEqualTo("Casa");
        assertThat(JsonPath.<Integer>read(created, "$.events.length()")).isEqualTo(1);

        assertThat(send("POST", bearer, "/api/deliveries", """
                {"trackingCode":"%s","name":"De novo"}""".formatted(code))).hasStatus(409);
        assertThat(send("POST", bearer, "/api/deliveries", """
                {"trackingCode":"ab","name":"Curto"}""")).hasStatus(400);

        remote.put(code, info("OutForDelivery", "Objeto saiu para entrega", at(1, 8)));
        sync.sync(List.of(UUID.fromString(id)));
        assertThat(send("GET", bearer, "/api/deliveries/summary", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.active").asNumber().isEqualTo(1);
            assertThat(json).extractingPath("$.today[0].id").isEqualTo(id);
        });
        assertThat(send("GET", bearer, "/api/notifications?size=5", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items[0].type").isEqualTo("DELIVERY_UPDATE");
            assertThat(json).extractingPath("$.items[0].data.status").isEqualTo("OUT_FOR_DELIVERY");
        });

        remote.put(code, info("InTransit", "Aguardando pagamento do despacho postal", at(1, 9)));
        sync.sync(List.of(UUID.fromString(id)));
        assertThat(send("GET", bearer, "/api/deliveries/" + id, null)).bodyJson().extractingPath("$.taxPending").isEqualTo(true);

        assertThat(send("PUT", bearer, "/api/deliveries/" + id + "/archive", "{\"archived\":true}")).hasStatusOk();
        assertThat(send("GET", bearer, "/api/deliveries", null)).bodyJson().extractingPath("$.length()").isEqualTo(0);
        assertThat(send("GET", bearer, "/api/deliveries?archived=true", null)).bodyJson().extractingPath("$[0].id").isEqualTo(id);

        assertThat(uploadImage(bearer, "/api/deliveries/" + id + "/image", png(64))).hasStatusOk();
        assertThat(send("GET", bearer, "/api/account/export", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.deliveries.deliveries[0].trackingCode").isEqualTo(code);
            assertThat(json).extractingPath("$.deliveries.addresses.length()").isEqualTo(2);
        });

        assertThat(send("DELETE", bearer, "/api/deliveries/addresses/" + work, null)).hasStatus(204);
        assertThat(send("GET", bearer, "/api/deliveries/addresses", null)).bodyJson()
                .extractingPath("$[0].defaultAddress").isEqualTo(true);
        assertThat(send("DELETE", bearer, "/api/deliveries/" + id, null)).hasStatus(204);
    }

    @Test
    void sharesWithTheGroupAndIsolatesOthers() {
        String alice = registerAndGetBearer("Alice", newUsername());
        String bob = registerAndGetBearer("Bob", newUsername());
        String carol = registerAndGetBearer("Carol", newUsername());
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", bob, "/api/groups/join/" + token, null)).hasStatus2xxSuccessful();
        String code = code();
        remote.put(code, info("InTransit", "Em trânsito", at(3, 10)));
        String address = JsonPath.read(post(alice, "/api/deliveries/addresses", """
                {"label":"Casa","cep":"01001000","street":"Rua A","number":"10","city":"São Paulo","state":"SP"}"""), "$.id");
        String id = JsonPath.read(post(alice, "/api/deliveries", """
                {"trackingCode":"%s","name":"Sofá","addressId":"%s","groupId":"%s"}""".formatted(code, address, groupId)), "$.id");

        assertThat(send("GET", bob, "/api/deliveries", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$[0].id").isEqualTo(id);
            assertThat(json).extractingPath("$[0].mine").isEqualTo(false);
            assertThat(json).extractingPath("$[0].address").isNull();
            assertThat(json).extractingPath("$[0].addressShort.city").isEqualTo("São Paulo");
            assertThat(json).extractingPath("$[0].group.name").isEqualTo("Casa");
        });
        assertThat(send("PUT", bob, "/api/deliveries/" + id, """
                {"name":"Meu"}""")).hasStatus(403);
        assertThat(send("GET", carol, "/api/deliveries/" + id, null)).hasStatus(404);
        assertThat(send("DELETE", carol, "/api/deliveries/" + id, null)).hasStatus(404);
        assertThat(send("PUT", carol, "/api/deliveries/addresses/" + address, """
                {"label":"X","cep":"01001000","street":"Rua","city":"São Paulo","state":"SP"}""")).hasStatus(404);
        assertThat(send("POST", carol, "/api/deliveries", """
                {"trackingCode":"%s","name":"X","groupId":"%s"}""".formatted(code(), groupId))).hasStatus(404);
        assertThat(send("POST", carol, "/api/deliveries", """
                {"trackingCode":"%s","name":"X","addressId":"%s"}""".formatted(code(), address))).hasStatus(400);

        remote.put(code, info("Delivered", "Objeto entregue ao destinatário", at(1, 11)));
        sync.sync(List.of(UUID.fromString(id)));
        assertThat(send("GET", bob, "/api/notifications?size=5", null)).bodyJson()
                .extractingPath("$.items[0].data.status").isEqualTo("DELIVERED");

        assertThat(send("DELETE", alice, "/api/groups/" + groupId + "/members/" + userId(bob), null)).hasStatus2xxSuccessful();
        assertThat(send("GET", bob, "/api/deliveries", null)).bodyJson().extractingPath("$.length()").isEqualTo(0);
        assertThat(mvc.get().uri("/api/deliveries").header(CSRF, "test").contentType(MediaType.APPLICATION_JSON)).hasStatus(401);
    }

    @Test
    void fixesTheShopeeCarrierAndKeepsManualSteps() throws Exception {
        String bearer = registerAndGetBearer();
        String other = registerAndGetBearer();
        String code = "BR" + String.valueOf(System.nanoTime()).substring(0, 12) + "Y";
        remote.put(code, info("NotFound", "", "", 100457));
        String id = JsonPath.read(post(bearer, "/api/deliveries", """
                {"trackingCode":"%s","name":"Kit limpeza"}""".formatted(code)), "$.id");
        verify(track17).register(argThat(m -> Integer.valueOf(101332).equals(m.get(code))));

        // Registro antigo, feito sem transportadora: a próxima leitura troca para a SPX uma vez só.
        var legacy = deliveries.findById(UUID.fromString(id)).orElseThrow();
        legacy.setCarrierRequested(null);
        deliveries.save(legacy);
        sync.sync(List.of(UUID.fromString(id)));
        sync.sync(List.of(UUID.fromString(id)));
        verify(track17, times(1)).changeCarrier(argThat(m -> Integer.valueOf(101332).equals(m.get(code))));

        String step = body(send("POST", bearer, "/api/deliveries/" + id + "/steps", """
                {"status":"IN_TRANSIT","text":"Chegou em Natal","location":"Natal - RN"}"""));
        assertThat(JsonPath.<String>read(step, "$.status")).isEqualTo("IN_TRANSIT");
        assertThat(JsonPath.<String>read(step, "$.trackedStatus")).isEqualTo("PENDING");
        assertThat(JsonPath.<String>read(step, "$.latestText")).isEqualTo("Chegou em Natal");
        String delivered = body(send("POST", bearer, "/api/deliveries/" + id + "/steps", """
                {"status":"DELIVERED"}"""));
        assertThat(JsonPath.<String>read(delivered, "$.status")).isEqualTo("DELIVERED");
        assertThat(JsonPath.<String>read(delivered, "$.events[0].text")).isEqualTo("Entregue");
        assertThat(JsonPath.<Object>read(delivered, "$.deliveredAt")).isNotNull();
        String deliveredStep = JsonPath.read(delivered, "$.events[0].id");

        remote.put(code, info("InTransit", "Em rota", at(1, 10), 101332));
        sync.sync(List.of(UUID.fromString(id)));
        assertThat(send("GET", bearer, "/api/deliveries/" + id, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.status").isEqualTo("DELIVERED");
            assertThat(json).extractingPath("$.trackedStatus").isEqualTo("IN_TRANSIT");
            assertThat(json).extractingPath("$.events.length()").isEqualTo(3);
        });
        assertThat(send("DELETE", bearer, "/api/deliveries/" + id + "/steps/" + deliveredStep, null)).bodyJson()
                .extractingPath("$.status").isEqualTo("IN_TRANSIT");

        assertThat(send("POST", bearer, "/api/deliveries/" + id + "/steps", """
                {"status":"PENDING"}""")).hasStatus(400);
        assertThat(send("POST", bearer, "/api/deliveries/" + id + "/steps", """
                {"status":"DELIVERED","at":"2099-01-01T10:00:00Z"}""")).hasStatus(400);
        assertThat(send("POST", other, "/api/deliveries/" + id + "/steps", """
                {"status":"DELIVERED"}""")).hasStatus(404);

        assertThat(send("PUT", bearer, "/api/deliveries/" + id + "/carrier", """
                {"carrier":2151}""")).bodyJson().extractingPath("$.carrierChoice").isEqualTo(2151);
        verify(track17).changeCarrier(argThat(m -> Integer.valueOf(2151).equals(m.get(code))));
        assertThat(send("PUT", bearer, "/api/deliveries/" + id + "/carrier", """
                {"carrier":999}""")).hasStatus(400);
        verify(track17, never()).changeCarrier(argThat(m -> Integer.valueOf(999).equals(m.get(code))));
    }

    private static String code() {
        return "LB" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase() + "BR";
    }

    private static JsonNode info(String status, String text, String time) {
        return info(status, text, time, 2151);
    }

    /** Sem {@code time}: sem eventos (código ainda não encontrado). */
    private static JsonNode info(String status, String text, String time, int carrier) {
        if (time.isEmpty()) {
            return JSON.readTree("""
                    {"latest_status":{"status":"%s","sub_status":"NotFound_Other"},
                     "tracking":{"providers":[{"provider":{"key":%d,"name":"Transportadora"},"events":[]}]}}
                    """.formatted(status, carrier));
        }
        return JSON.readTree("""
                {"latest_status":{"status":"%s"},
                 "latest_event":{"time_iso":"%s","description":"%s","location":"SAO PAULO - SP"},
                 "tracking":{"providers":[{"provider":{"key":%d,"name":"Correios","homepage":"https://www.correios.com.br"},
                   "events":[{"time_iso":"%s","description":"%s","location":"SAO PAULO - SP"}]}]}}
                """.formatted(status, time, text, carrier, time, text));
    }

    /** Instante de {@code daysAgo} dias atrás na hora dada (eventos recentes: a entrega não conta como parada). */
    private static String at(int daysAgo, int hour) {
        return LocalDate.now(ZoneOffset.ofHours(-3)).minusDays(daysAgo).atTime(hour, 0).atOffset(ZoneOffset.ofHours(-3)).toString();
    }
}
