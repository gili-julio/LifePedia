package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.delivery.CepClient;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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

    private final Map<String, JsonNode> remote = new ConcurrentHashMap<>();

    @BeforeEach
    void mockTracking() {
        when(track17.enabled()).thenReturn(true);
        try {
            when(track17.register(anyList()))
                    .thenAnswer(call -> new Registered(new HashSet<>(call.<List<String>>getArgument(0)), Map.of()));
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
        remote.put(code, info("InTransit", "Objeto em transferência", "2026-11-10T10:00:00-03:00"));
        when(cep.find("59000000")).thenReturn(Optional.of(new CepClient.Address("59000000", "Rua A", "Centro", "Natal", "RN")));

        assertThat(send("GET", bearer, "/api/deliveries/cep/59000-000", null)).bodyJson()
                .extractingPath("$.city").isEqualTo("Natal");
        assertThat(send("GET", bearer, "/api/deliveries/cep/12345678", null)).hasStatus(404);
        String home = JsonPath.read(post(bearer, "/api/deliveries/addresses", """
                {"label":"Casa","cep":"59000-000","street":"Rua A","number":"10","city":"Natal","state":"rn"}"""), "$.id");
        String work = JsonPath.read(post(bearer, "/api/deliveries/addresses", """
                {"label":"Trabalho","cep":"59000001","street":"Rua B","city":"Natal","state":"RN","defaultAddress":true}"""), "$.id");
        assertThat(send("GET", bearer, "/api/deliveries/addresses", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$[0].id").isEqualTo(work);
            assertThat(json).extractingPath("$[1].defaultAddress").isEqualTo(false);
            assertThat(json).extractingPath("$[1].state").isEqualTo("RN");
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

        remote.put(code, info("OutForDelivery", "Objeto saiu para entrega", "2026-11-12T08:00:00-03:00"));
        sync.sync(List.of(UUID.fromString(id)));
        assertThat(send("GET", bearer, "/api/deliveries/summary", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.active").asNumber().isEqualTo(1);
            assertThat(json).extractingPath("$.today[0].id").isEqualTo(id);
        });
        assertThat(send("GET", bearer, "/api/notifications?size=5", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items[0].type").isEqualTo("DELIVERY_UPDATE");
            assertThat(json).extractingPath("$.items[0].data.status").isEqualTo("OUT_FOR_DELIVERY");
        });

        remote.put(code, info("InTransit", "Aguardando pagamento do despacho postal", "2026-11-12T09:00:00-03:00"));
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
        remote.put(code, info("InTransit", "Em trânsito", "2026-11-10T10:00:00-03:00"));
        String address = JsonPath.read(post(alice, "/api/deliveries/addresses", """
                {"label":"Casa","cep":"59000000","street":"Rua A","number":"10","city":"Natal","state":"RN"}"""), "$.id");
        String id = JsonPath.read(post(alice, "/api/deliveries", """
                {"trackingCode":"%s","name":"Sofá","addressId":"%s","groupId":"%s"}""".formatted(code, address, groupId)), "$.id");

        assertThat(send("GET", bob, "/api/deliveries", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$[0].id").isEqualTo(id);
            assertThat(json).extractingPath("$[0].mine").isEqualTo(false);
            assertThat(json).extractingPath("$[0].address").isNull();
            assertThat(json).extractingPath("$[0].addressShort.city").isEqualTo("Natal");
            assertThat(json).extractingPath("$[0].group.name").isEqualTo("Casa");
        });
        assertThat(send("PUT", bob, "/api/deliveries/" + id, """
                {"name":"Meu"}""")).hasStatus(403);
        assertThat(send("GET", carol, "/api/deliveries/" + id, null)).hasStatus(404);
        assertThat(send("DELETE", carol, "/api/deliveries/" + id, null)).hasStatus(404);
        assertThat(send("PUT", carol, "/api/deliveries/addresses/" + address, """
                {"label":"X","cep":"59000000","street":"Rua","city":"Natal","state":"RN"}""")).hasStatus(404);
        assertThat(send("POST", carol, "/api/deliveries", """
                {"trackingCode":"%s","name":"X","groupId":"%s"}""".formatted(code(), groupId))).hasStatus(404);
        assertThat(send("POST", carol, "/api/deliveries", """
                {"trackingCode":"%s","name":"X","addressId":"%s"}""".formatted(code(), address))).hasStatus(400);

        remote.put(code, info("Delivered", "Objeto entregue ao destinatário", "2026-11-12T11:00:00-03:00"));
        sync.sync(List.of(UUID.fromString(id)));
        assertThat(send("GET", bob, "/api/notifications?size=5", null)).bodyJson()
                .extractingPath("$.items[0].data.status").isEqualTo("DELIVERED");

        assertThat(send("DELETE", alice, "/api/groups/" + groupId + "/members/" + userId(bob), null)).hasStatus2xxSuccessful();
        assertThat(send("GET", bob, "/api/deliveries", null)).bodyJson().extractingPath("$.length()").isEqualTo(0);
        assertThat(mvc.get().uri("/api/deliveries").header(CSRF, "test").contentType(MediaType.APPLICATION_JSON)).hasStatus(401);
    }

    private static String code() {
        return "LB" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase() + "BR";
    }

    private static JsonNode info(String status, String text, String time) {
        return JSON.readTree("""
                {"latest_status":{"status":"%s"},
                 "latest_event":{"time_iso":"%s","description":"%s","location":"NATAL - RN"},
                 "tracking":{"providers":[{"provider":{"key":2151,"name":"Correios","homepage":"https://www.correios.com.br"},
                   "events":[{"time_iso":"%s","description":"%s","location":"NATAL - RN"}]}]}}
                """.formatted(status, time, text, time, text));
    }
}
