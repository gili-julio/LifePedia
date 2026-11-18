package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.planning.LinkPreviewService;
import com.lifepedia.api.planning.LinkPreviewService.StorePrice;
import com.lifepedia.api.planning.PriceReferenceService;
import com.lifepedia.api.price.PriceCheckService;
import com.lifepedia.api.price.PriceFixtures;
import com.lifepedia.api.shared.net.SafeHttpClient;
import com.lifepedia.api.shared.net.SafeHttpClient.FetchException;
import com.lifepedia.api.shared.net.SafeHttpClient.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PriceIntegrationTest extends ApiTestSupport {

    private static final String STORE_URL = "https://loja.exemplo.com.br/fone-jbl";

    @MockitoBean
    SafeHttpClient http;

    @MockitoBean
    PriceReferenceService references;

    @MockitoBean
    LinkPreviewService stores;

    @Autowired
    PriceCheckService checks;

    private final AtomicReference<BigDecimal> kabum = new AtomicReference<>(new BigDecimal("207.92"));

    @BeforeEach
    void mockSources() throws FetchException {
        when(references.findProductUrl(anyString())).thenReturn(Optional.of(PriceFixtures.PRODUCT_URL));
        doThrow(new FetchException("Fora do teste.")).when(http).get(any(), anyString(), anyInt(), any());
        doAnswer(call -> new Response(URI.create(PriceFixtures.PRODUCT_URL), "text/html", StandardCharsets.UTF_8,
                PriceFixtures.product(kabum.get()).getBytes(StandardCharsets.UTF_8)))
                .when(http).get(eq(URI.create(PriceFixtures.PRODUCT_URL)), anyString(), anyInt(), any());
        when(stores.storePrice(STORE_URL)).thenReturn(new StorePrice("Loja Exemplo", "Fone JBL", new BigDecimal("210.00"), false));
    }

    @Test
    void tracksStoresHistoryManualPricesAndAlerts() {
        String alice = registerAndGetBearer();
        String created = post(alice, "/api/prices", """
                {"name":"Fone JBL Tune 520BT","settings":{"targetPrice":200},"links":["%s"]}""".formatted(STORE_URL));
        String id = JsonPath.read(created, "$.watch.id");
        assertThat(JsonPath.<Double>read(created, "$.watch.bestPrice")).isEqualTo(207.92);
        assertThat(JsonPath.<String>read(created, "$.watch.bestStore")).isEqualTo("KaBuM!");
        assertThat(JsonPath.<List<String>>read(created, "$.offers[*].store")).containsExactly("KaBuM!", "Loja Exemplo", "Amazon");
        assertThat(JsonPath.<String>read(created, "$.buscapeUrl")).isEqualTo(PriceFixtures.PRODUCT_URL);
        assertThat(JsonPath.<Integer>read(created, "$.history.length()")).isEqualTo(3);
        assertThat(JsonPath.<Double>read(created, "$.links[0].lastPrice")).isEqualTo(210.0);
        assertThat(JsonPath.<Boolean>read(created, "$.watch.atTarget")).isFalse();

        String manual = body(send("POST", alice, "/api/prices/" + id + "/prices", """
                {"store":"Feira","price":205}"""));
        assertThat(JsonPath.<String>read(manual, "$.watch.bestStore")).isEqualTo("Feira");
        String pointId = JsonPath.read(manual, "$.manual[0].id");
        assertThat(send("POST", alice, "/api/prices/" + id + "/refresh", null)).hasStatus(429);

        kabum.set(new BigDecimal("189.90"));
        checks.check(List.of(UUID.fromString(id)));
        assertThat(send("GET", alice, "/api/notifications?size=5", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items[0].type").isEqualTo("PRICE_ALERT");
            assertThat(json).extractingPath("$.items[0].data.kind").isEqualTo("TARGET");
            assertThat(json).extractingPath("$.items[0].data.store").isEqualTo("KaBuM!");
        });
        checks.check(List.of(UUID.fromString(id)));
        assertThat(send("GET", alice, "/api/notifications?size=5", null)).bodyJson()
                .extractingPath("$.totalItems").isEqualTo(1);

        assertThat(send("DELETE", alice, "/api/prices/" + id + "/prices/" + pointId, null)).bodyJson()
                .extractingPath("$.manual.length()").isEqualTo(0);
        assertThat(send("GET", alice, "/api/search?q=jbl", null)).bodyJson()
                .extractingPath("$.groups[0].kind").isEqualTo("PRICE_WATCH");

        String bob = registerAndGetBearer();
        assertThat(send("GET", bob, "/api/prices/" + id, null)).hasStatus(404);
        assertThat(send("GET", bob, "/api/prices", null)).bodyJson().extractingPath("$.length()").isEqualTo(0);
        assertThat(send("POST", alice, "/api/prices/" + id + "/links", """
                {"url":"javascript:alert(1)"}""")).hasStatus(400);
    }

    @Test
    void groupWatchUpdatesThePlanAndRespectsRoles() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String carol = registerAndGetBearer();
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", bob, "/api/groups/join/" + token, null)).hasStatus2xxSuccessful();
        String planItem = JsonPath.read(post(alice, "/api/groups/" + groupId + "/planning/items", """
                {"name":"Fone","kind":"PURCHASE","amount":300,"targetMonth":"%s"}""".formatted(YearMonth.now().plusMonths(2))), "$.id");

        String id = JsonPath.read(post(alice, "/api/prices", """
                {"name":"Fone JBL","groupId":"%s","planItemId":"%s","settings":{"updatePlan":true,"notifyGroup":true}}"""
                .formatted(groupId, planItem)), "$.watch.id");
        assertThat(send("GET", alice, "/api/groups/" + groupId + "/planning", null)).bodyJson()
                .extractingPath("$.items[0].item.amount").asNumber().isEqualTo(207.92);
        assertThat(send("POST", alice, "/api/prices", """
                {"name":"De novo","groupId":"%s","planItemId":"%s"}""".formatted(groupId, planItem))).hasStatus(409);

        assertThat(send("GET", bob, "/api/prices?groupId=" + groupId, null)).bodyJson()
                .extractingPath("$[0].canDelete").isEqualTo(false);
        assertThat(send("POST", bob, "/api/prices/" + id + "/prices", """
                {"store":"Mercado","price":150}""")).hasStatus2xxSuccessful();
        assertThat(send("GET", alice, "/api/groups/" + groupId + "/planning", null)).bodyJson()
                .extractingPath("$.items[0].item.amount").asNumber().isEqualTo(150.0);
        assertThat(send("DELETE", bob, "/api/prices/" + id, null)).hasStatus(403);
        assertThat(send("GET", carol, "/api/prices?groupId=" + groupId, null)).hasStatus(404);
        assertThat(send("PUT", carol, "/api/prices/" + id, """
                {"name":"X","settings":{}}""")).hasStatus(404);
        assertThat(send("DELETE", alice, "/api/prices/" + id, null)).hasStatus(204);
    }
}
