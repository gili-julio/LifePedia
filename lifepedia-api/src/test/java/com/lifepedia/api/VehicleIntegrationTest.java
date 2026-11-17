package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.vehicle.FipeClient;
import com.lifepedia.api.vehicle.FipeClient.Price;
import com.lifepedia.api.vehicle.VehicleReminderJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class VehicleIntegrationTest extends ApiTestSupport {

    @MockitoBean
    FipeClient fipe;

    @Autowired
    VehicleReminderJob reminders;

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Test
    void vehicleWithKmFuelFinanceMaintenanceAndDocuments() throws Exception {
        when(fipe.price(any(), anyString(), anyString(), anyString())).thenReturn(Optional.of(
                new Price("005340-6", new BigDecimal("54321.00"), "VW", "Gol 1.0", 2018, "Flex", "novembro de 2026")));
        String me = registerAndGetBearer();
        String transport = categoryId(me, "EXPENSE", "Transporte");

        String id = JsonPath.read(post(me, "/api/vehicles", """
                {"type":"CAR","name":"Gol","plate":"abc-1d23","odometer":10000,"template":true,
                 "fipe":{"kind":"CARS","brand":"59","model":"5940","year":"2018-5"}}"""), "$.vehicle.id");
        String base = "/api/vehicles/" + id;
        assertThat(send("GET", me, base, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.vehicle.plate").isEqualTo("ABC-1D23");
            assertThat(json).extractingPath("$.vehicle.odometer").isEqualTo(10000);
            assertThat(json).extractingPath("$.vehicle.fipeValue").isEqualTo(54321.0);
            assertThat(json).extractingPath("$.fipe.code").isEqualTo("005340-6");
            assertThat(json).extractingPath("$.items.length()").isEqualTo(13);
        });
        assertThat(send("POST", me, "/api/vehicles", """
                {"type":"BICYCLE","name":"Bike","fipe":{"kind":"CARS","brand":"1","model":"1","year":"2018-1"}}""")).hasStatus(400);

        // Km: leitura antiga dá o ritmo; km fora de ordem é recusado; data no futuro também.
        post(me, base + "/readings", """
                {"km":9000,"date":"%s"}""".formatted(today.minusDays(30)));
        assertThat(send("POST", me, base + "/readings", """
                {"km":8000,"date":"%s"}""".formatted(today))).hasStatus(400);
        assertThat(send("POST", me, base + "/readings", """
                {"km":12000,"date":"%s"}""".formatted(today.plusDays(1)))).hasStatus(400);
        assertThat(send("GET", me, base, null)).bodyJson().extractingPath("$.vehicle.kmPerDay").isNotNull();

        // Abastecimentos com tanque cheio: consumo e lançamento no financeiro.
        String fill = """
                {"kind":"FUEL","date":"%s","amount":%s,"odometer":%d,"liters":%s,"fullTank":true,"fuel":"GASOLINE",
                 "finance":{"categoryId":"%s"}}""";
        post(me, base + "/expenses", fill.formatted(today.minusDays(20), "180.00", 9300, "30", transport));
        String second = JsonPath.read(post(me, base + "/expenses",
                fill.formatted(today.minusDays(10), "210.00", 9700, "35", transport)), "$.id");
        assertThat(send("GET", me, base + "/stats?months=3", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.avgKmPerLiter").isEqualTo(11.43);
            assertThat(json).extractingPath("$.total").isEqualTo(390.0);
            assertThat(json).extractingPath("$.consumption.length()").isEqualTo(1);
        });
        YearMonth month = YearMonth.from(today.minusDays(10));
        String transactions = body(send("GET", me, "/api/finance/transactions?size=50&month=" + month, null));
        assertThat(JsonPath.<List<String>>read(transactions, "$.items[*].description")).contains("Abastecimento - Gol");

        // Edição sem financeiro tira o lançamento; excluir apaga a leitura do km junto.
        assertThat(send("PUT", me, base + "/expenses/" + second, """
                {"kind":"FUEL","date":"%s","amount":215.00,"odometer":9700,"liters":35,"fullTank":true}"""
                .formatted(today.minusDays(10)))).bodyJson().extractingPath("$.finance").isNull();
        assertThat(JsonPath.<List<String>>read(body(send("GET", me, "/api/finance/transactions?size=50&month=" + month, null)),
                "$.items[?(@.amount == 210.0)].description")).isEmpty();

        // Manutenção feita com gasto e histórico.
        String detail = body(send("GET", me, base, null));
        String oil = JsonPath.<List<String>>read(detail, "$.items[?(@.name == 'Óleo do motor')].id").getFirst();
        assertThat(send("POST", me, base + "/items/" + oil + "/done", """
                {"date":"%s","km":10000,"amount":250.00,"shop":"Oficina do Zé"}""".formatted(today))).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items[?(@.name == 'Óleo do motor')].lastKm").asArray().containsExactly(10000);
            assertThat(json).extractingPath("$.items[?(@.name == 'Óleo do motor')].due.dueKm").asArray().containsExactly(20000);
        });
        assertThat(send("GET", me, base + "/logs", null)).bodyJson().extractingPath("$[0].amount").isEqualTo(250.0);

        // Plano de revisões pula as que já passaram do km atual.
        assertThat(send("POST", me, base + "/revisions/plan", """
                {"everyKm":10000,"everyMonths":12,"count":3}""")).bodyJson()
                .extractingPath("$.revisions[*].dueKm").asArray().containsExactly(20000, 30000, 40000);

        // Documento perto de vencer, aviso uma vez só e renovação com gasto.
        String docs = body(send("POST", me, base + "/documents", """
                {"kind":"IPVA","name":"IPVA","dueOn":"%s"}""".formatted(today.plusDays(10))));
        String ipva = JsonPath.<List<String>>read(docs, "$.documents[*].id").getFirst();
        assertThat(JsonPath.<String>read(docs, "$.upcoming[0].name")).isEqualTo("IPVA");
        reminders.remind();
        long warned = reminderCount(me);
        assertThat(warned).isGreaterThan(0);
        reminders.remind();
        assertThat(reminderCount(me)).isEqualTo(warned);
        assertThat(send("POST", me, base + "/documents/" + ipva + "/renew", """
                {"nextDue":"%s","amount":500.00}""".formatted(today.plusYears(1)))).bodyJson()
                .extractingPath("$.documents[0].due.stage").isEqualTo("OK");
        assertThat(send("GET", me, base + "/expenses", null)).bodyJson()
                .extractingPath("$[?(@.kind == 'TAX')].amount").asArray().containsExactly(500.0);

        assertThat(mvc.get().uri(base)).hasStatus(401);
    }

    @Test
    void sharingWithFriendsAndGroupsByRole() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String carol = registerAndGetBearer();
        String id = JsonPath.read(post(alice, "/api/vehicles", """
                {"type":"MOTORCYCLE","name":"Moto","odometer":5000}"""), "$.vehicle.id");
        String base = "/api/vehicles/" + id;

        assertThat(send("GET", bob, base, null)).hasStatus(404);
        assertThat(send("PUT", alice, base + "/shares/users/" + userId(bob), """
                {"role":"VIEW"}""")).hasStatus(400);
        befriend(alice, bob);
        assertThat(send("PUT", alice, base + "/shares/users/" + userId(bob), """
                {"role":"VIEW"}""")).hasStatus(204);
        assertThat(send("GET", bob, "/api/vehicles", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.shared[0].name").isEqualTo("Moto");
            assertThat(json).extractingPath("$.shared[0].mine").isEqualTo(false);
            assertThat(json).extractingPath("$.shared[0].owner.id").isEqualTo(userId(alice));
        });
        assertThat(send("GET", bob, "/api/notifications?size=5", null)).bodyJson()
                .extractingPath("$.items[0].type").isEqualTo("VEHICLE_SHARED");
        String reading = """
                {"km":5100,"date":"%s"}""".formatted(today);
        assertThat(send("POST", bob, base + "/readings", reading)).hasStatus(403);

        assertThat(send("PUT", alice, base + "/shares/users/" + userId(bob), """
                {"role":"LOG"}""")).hasStatus(204);
        assertThat(send("POST", bob, base + "/readings", reading)).hasStatus(201);
        String expense = """
                {"kind":"WASH","date":"%s","amount":40.00}""".formatted(today);
        String bobs = JsonPath.read(post(bob, base + "/expenses", expense), "$.id");
        String alices = JsonPath.read(post(alice, base + "/expenses", expense), "$.id");
        assertThat(send("PUT", bob, base + "/expenses/" + alices, expense)).hasStatus(403);
        assertThat(send("PUT", bob, base, """
                {"type":"MOTORCYCLE","name":"Minha"}""")).hasStatus(403);
        assertThat(send("GET", bob, base + "/shares", null)).hasStatus(403);
        assertThat(send("DELETE", alice, base + "/expenses/" + bobs, null)).hasStatus(204);

        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", carol, "/api/groups/join/" + token, null)).hasStatusOk();
        assertThat(send("GET", carol, base, null)).hasStatus(404);
        assertThat(send("PUT", alice, base + "/shares/groups/" + groupId, """
                {"role":"EDIT"}""")).hasStatus(204);
        assertThat(send("GET", carol, base, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.vehicle.role").isEqualTo("EDIT");
            assertThat(json).extractingPath("$.vehicle.sharedVia").isEqualTo("Casa");
        });
        assertThat(send("PUT", carol, base, """
                {"type":"MOTORCYCLE","name":"Moto da casa"}""")).hasStatusOk();
        assertThat(send("DELETE", carol, base, null)).hasStatus(403);

        assertThat(send("DELETE", bob, base + "/shares/me", null)).hasStatus(204);
        assertThat(send("GET", bob, base, null)).hasStatus(404);
        assertThat(send("DELETE", alice, base, null)).hasStatus(204);
        assertThat(send("GET", carol, base, null)).hasStatus(404);
    }

    private long reminderCount(String bearer) {
        String json = body(send("GET", bearer, "/api/notifications?size=50", null));
        return JsonPath.<List<Object>>read(json, "$.items[?(@.type == 'VEHICLE_REMINDER')]").size();
    }

    private void befriend(String a, String b) {
        send("POST", a, "/api/friends/requests", """
                {"userId":"%s"}""".formatted(userId(b)));
        assertThat(send("POST", b, "/api/friends/requests", """
                {"userId":"%s"}""".formatted(userId(a)))).bodyJson().extractingPath("$.status").isEqualTo("ACCEPTED");
    }
}
