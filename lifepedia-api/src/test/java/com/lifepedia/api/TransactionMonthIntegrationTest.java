package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TransactionMonthIntegrationTest extends ApiTestSupport {

    private final YearMonth month = YearMonth.of(2026, 9);

    @Test
    void cardPurchaseCountsInBillMonth() {
        String bearer = registerAndGetBearer();
        String food = categoryId(bearer, "EXPENSE", "Alimentação");

        assertThat(send("POST", bearer, "/api/finance/import", """
                {"transactions":[
                  {"description":"Mercado","type":"EXPENSE","categoryId":"%1$s","amount":100,"date":"2026-08-28","month":"%2$s"},
                  {"description":"Loja (10/12)","type":"EXPENSE","categoryId":"%1$s","amount":30,"date":"2025-12-07","month":"%2$s"},
                  {"description":"Padaria","type":"EXPENSE","categoryId":"%1$s","amount":20,"date":"2026-09-02"}],
                 "fixedAdjustments":[]}
                """.formatted(food, month))).hasStatus(201);

        assertThat(send("GET", bearer, "/api/finance/transactions/all?month=" + month, null))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.length()").isEqualTo(3);
                    assertThat(json).extractingPath("$[0].month").isEqualTo(month.toString());
                });
        assertThat(send("GET", bearer, "/api/finance/summary?month=" + month, null))
                .bodyJson().extractingPath("$.occasional.expense").asNumber().isEqualTo(150.0);
        assertThat(send("GET", bearer, "/api/finance/summary?month=" + month.minusMonths(1), null))
                .bodyJson().extractingPath("$.occasional.expense").asNumber().isEqualTo(0);
        assertThat(send("GET", bearer, "/api/finance/summary/history?until=" + month + "&months=2", null))
                .bodyJson().extractingPath("$[1].occasionalExpense").asNumber().isEqualTo(150.0);
    }

    @Test
    void keepsOriginalDescriptionAndListsNames() {
        String bearer = registerAndGetBearer();
        String food = categoryId(bearer, "EXPENSE", "Alimentação");
        String day = YearMonth.now().atDay(1).toString();
        assertThat(send("POST", bearer, "/api/finance/import", """
                {"transactions":[
                  {"description":"Natal IMD","originalDescription":"PAG*IMD NATAL RN","type":"EXPENSE","categoryId":"%1$s","amount":12,"date":"%2$s"},
                  {"description":"natal imd ","type":"EXPENSE","categoryId":"%1$s","amount":8,"date":"%2$s"},
                  {"description":"Padaria","type":"EXPENSE","categoryId":"%1$s","amount":5,"date":"%2$s"}],
                 "fixedAdjustments":[]}
                """.formatted(food, day))).hasStatus(201);

        List<String> ids = JsonPath.read(body(send("GET", bearer, "/api/finance/transactions/all", null)),
                "$[?(@.originalDescription == 'PAG*IMD NATAL RN')].id");
        assertThat(ids).hasSize(1);
        assertThat(send("PUT", bearer, "/api/finance/transactions/" + ids.getFirst(), """
                {"description":"IMD","originalDescription":"outra","type":"EXPENSE","categoryId":"%s","amount":12,"date":"%s"}
                """.formatted(food, day))).bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.description").isEqualTo("IMD");
                    assertThat(json).extractingPath("$.originalDescription").isEqualTo("PAG*IMD NATAL RN");
                });

        assertThat(send("GET", bearer, "/api/finance/transactions/descriptions", null))
                .bodyJson().satisfies(json -> assertThat(json).extractingPath("$.length()").isEqualTo(3));
    }

    @Test
    void movesInstallmentAloneOrWithTheFollowingOnes() {
        String bearer = registerAndGetBearer();
        String other = registerAndGetBearer();
        String food = categoryId(bearer, "EXPENSE", "Alimentação");
        assertThat(send("POST", bearer, "/api/finance/import", """
                {"transactions":[
                  {"description":"Loja (1/4)","type":"EXPENSE","categoryId":"%1$s","amount":25,"date":"2026-01-10","month":"2026-02"},
                  {"description":"Loja (2/4)","type":"EXPENSE","categoryId":"%1$s","amount":25,"date":"2026-01-10","month":"2026-03"},
                  {"description":"Loja (3/4)","type":"EXPENSE","categoryId":"%1$s","amount":25,"date":"2026-01-10","month":"2026-04"},
                  {"description":"Loja (4/4)","type":"EXPENSE","categoryId":"%1$s","amount":25,"date":"2026-01-10","month":"2026-05"},
                  {"description":"Loja (2/3)","type":"EXPENSE","categoryId":"%1$s","amount":9,"date":"2026-01-10","month":"2026-03"},
                  {"description":"Padaria","type":"EXPENSE","categoryId":"%1$s","amount":5,"date":"2026-03-02"}],
                 "fixedAdjustments":[]}
                """.formatted(food))).hasStatus(201);
        String all = body(send("GET", bearer, "/api/finance/transactions/all?from=2026-01", null));
        String second = idOf(all, "Loja (2/4)");
        String fourth = idOf(all, "Loja (4/4)");

        // Só esta: adia a 4/4 para junho.
        assertThat(send("PUT", bearer, "/api/finance/transactions/" + fourth + "/month", """
                {"month":"2026-06"}""")).bodyJson().extractingPath("$.moved").isEqualTo(1);
        // Esta e as seguintes: adianta a 2/4 um mês; 3/4 e 4/4 vão junto, a outra compra (2/3) não.
        assertThat(send("PUT", bearer, "/api/finance/transactions/" + second + "/month", """
                {"month":"2026-02","following":true}""")).bodyJson().extractingPath("$.moved").isEqualTo(3);

        String after = body(send("GET", bearer, "/api/finance/transactions/all?from=2026-01", null));
        assertThat(monthOf(after, "Loja (2/4)")).isEqualTo("2026-02");
        assertThat(monthOf(after, "Loja (3/4)")).isEqualTo("2026-03");
        assertThat(monthOf(after, "Loja (4/4)")).isEqualTo("2026-05");
        assertThat(monthOf(after, "Loja (2/3)")).isEqualTo("2026-03");

        // Antes do mês da compra não pode; lançamento sem parcela só move sozinho; de outro usuário: 404.
        assertThat(send("PUT", bearer, "/api/finance/transactions/" + second + "/month", """
                {"month":"2025-12"}""")).hasStatus(400);
        assertThat(send("PUT", bearer, "/api/finance/transactions/" + idOf(after, "Padaria") + "/month", """
                {"month":"2026-04","following":true}""")).hasStatus(400);
        assertThat(send("PUT", bearer, "/api/finance/transactions/" + second + "/month", "{}")).hasStatus(400);
        assertThat(send("PUT", other, "/api/finance/transactions/" + second + "/month", """
                {"month":"2026-04"}""")).hasStatus(404);
    }


    @Test
    void rejectsMonthBeforeDate() {
        String bearer = registerAndGetBearer();
        assertThat(send("POST", bearer, "/api/finance/transactions", """
                {"description":"Mercado","type":"EXPENSE","categoryId":"%s","amount":10,"date":"2026-09-10","month":"2026-08"}
                """.formatted(categoryId(bearer, "EXPENSE", "Alimentação")))).hasStatus(400);
    }

    private static String idOf(String json, String description) {
        List<String> ids = JsonPath.read(json, "$[?(@.description == '" + description + "')].id");
        return ids.getFirst();
    }

    private static String monthOf(String json, String description) {
        List<String> months = JsonPath.read(json, "$[?(@.description == '" + description + "')].month");
        return months.getFirst();
    }
}
