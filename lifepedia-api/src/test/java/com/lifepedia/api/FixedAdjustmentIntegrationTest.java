package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FixedAdjustmentIntegrationTest extends ApiTestSupport {

    private final YearMonth month = YearMonth.now();

    private String createFixed(String bearer, String category, double amount) {
        String body = post(bearer, "/api/finance/recurring", """
                {"description":"Luz","type":"EXPENSE","categoryId":"%s","amount":%s,"startMonth":"%s"}
                """.formatted(category, amount, month.minusMonths(2)));
        return JsonPath.read(body, "$.id");
    }

    @Test
    void adjustmentReplacesFixedAmountOnlyInThatMonth() {
        String bearer = registerAndGetBearer();
        String fixed = createFixed(bearer, categoryId(bearer, "EXPENSE", "Contas da casa"), 150);

        assertThat(send("PUT", bearer, "/api/finance/recurring/" + fixed + "/adjustments/" + month, """
                {"amount":182.40}
                """)).hasStatusOk();

        assertThat(send("GET", bearer, "/api/finance/summary?month=" + month, null))
                .bodyJson().extractingPath("$.fixed.expense").asNumber().isEqualTo(182.4);
        assertThat(send("GET", bearer, "/api/finance/summary?month=" + month.minusMonths(1), null))
                .bodyJson().extractingPath("$.fixed.expense").asNumber().isEqualTo(150.0);
        assertThat(send("GET", bearer, "/api/finance/recurring/all", null))
                .bodyJson().extractingPath("$[0].adjustments[0].amount").asNumber().isEqualTo(182.4);

        assertThat(send("DELETE", bearer, "/api/finance/recurring/" + fixed + "/adjustments/" + month, null)).hasStatus(204);
        assertThat(send("GET", bearer, "/api/finance/summary?month=" + month, null))
                .bodyJson().extractingPath("$.fixed.expense").asNumber().isEqualTo(150.0);
    }

    @Test
    void rejectsAdjustmentOutsideValidityOrFromOtherUser() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String fixed = createFixed(alice, categoryId(alice, "EXPENSE", "Contas da casa"), 150);

        assertThat(send("PUT", alice, "/api/finance/recurring/" + fixed + "/adjustments/" + month.minusMonths(6), """
                {"amount":100}
                """)).hasStatus(400);
        assertThat(send("PUT", bob, "/api/finance/recurring/" + fixed + "/adjustments/" + month, """
                {"amount":100}
                """)).hasStatus(404);
    }

    @Test
    void importSavesTransactionsAndAdjustmentsAtomically() {
        String bearer = registerAndGetBearer();
        String food = categoryId(bearer, "EXPENSE", "Alimentação");
        String fixed = createFixed(bearer, categoryId(bearer, "EXPENSE", "Contas da casa"), 150);

        assertThat(send("POST", bearer, "/api/finance/import", """
                {"transactions":[{"description":"Mercado","type":"EXPENSE","categoryId":"%s","amount":90,"date":"%s"}],
                 "fixedAdjustments":[{"recurringEntryId":"%s","month":"%s","amount":170}]}
                """.formatted(food, month.atDay(3), fixed, month)))
                .hasStatus(201).bodyJson().extractingPath("$.fixedAdjustments").isEqualTo(1);

        assertThat(send("GET", bearer, "/api/finance/summary?month=" + month, null))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.fixed.expense").asNumber().isEqualTo(170.0);
                    assertThat(json).extractingPath("$.occasional.expense").asNumber().isEqualTo(90.0);
                });

        // ajuste inválido (fora da vigência) desfaz também o lançamento
        assertThat(send("POST", bearer, "/api/finance/import", """
                {"transactions":[{"description":"Padaria","type":"EXPENSE","categoryId":"%s","amount":10,"date":"%s"}],
                 "fixedAdjustments":[{"recurringEntryId":"%s","month":"%s","amount":170}]}
                """.formatted(food, month.atDay(4), fixed, month.minusMonths(6)))).hasStatus(400);
        assertThat(send("GET", bearer, "/api/finance/transactions/all?month=" + month, null))
                .bodyJson().extractingPath("$.length()").isEqualTo(1);

        assertThat(send("POST", bearer, "/api/finance/import", "{}")).hasStatus(400);
    }
}
