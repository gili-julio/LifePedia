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
    void rejectsMonthBeforeDate() {
        String bearer = registerAndGetBearer();
        assertThat(send("POST", bearer, "/api/finance/transactions", """
                {"description":"Mercado","type":"EXPENSE","categoryId":"%s","amount":10,"date":"2026-09-10","month":"2026-08"}
                """.formatted(categoryId(bearer, "EXPENSE", "Alimentação")))).hasStatus(400);
    }
}
