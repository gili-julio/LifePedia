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
class InvestmentIntegrationTest extends ApiTestSupport {

    private final YearMonth now = YearMonth.now();

    @Test
    void investmentStaysOutOfExpensesButReducesBalance() {
        String bearer = registerAndGetBearer();
        String invest = JsonPath.read(post(bearer, "/api/finance/categories", """
                {"name":"Investír","type":"EXPENSE","color":"#008300"}"""), "$.id");
        post(bearer, "/api/finance/transactions", """
                {"description":"Salário","type":"INCOME","categoryId":"%s","amount":1000,"date":"%s"}
                """.formatted(categoryId(bearer, "INCOME", "Salário"), now.atDay(5)));
        post(bearer, "/api/finance/transactions", """
                {"description":"Mercado","type":"EXPENSE","categoryId":"%s","amount":200,"date":"%s"}
                """.formatted(categoryId(bearer, "EXPENSE", "Alimentação"), now.atDay(6)));
        post(bearer, "/api/finance/transactions", """
                {"description":"Tesouro","type":"EXPENSE","categoryId":"%s","amount":300,"date":"%s"}
                """.formatted(invest, now.atDay(7)));

        assertThat(send("GET", bearer, "/api/finance/summary?month=" + now, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.total.income").asNumber().isEqualTo(1000.0);
            assertThat(json).extractingPath("$.total.expense").asNumber().isEqualTo(200.0);
            assertThat(json).extractingPath("$.total.invested").asNumber().isEqualTo(300.0);
            assertThat(json).extractingPath("$.total.balance").asNumber().isEqualTo(500.0);
            assertThat(json).extractingPath("$.investedPercent").asNumber().isEqualTo(30.0);
            assertThat(json).extractingPath("$.expensesByCategory.length()").isEqualTo(1);
        });
        assertThat(send("GET", bearer, "/api/finance/summary/history?until=" + now + "&months=2", null))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$[1].invested").asNumber().isEqualTo(300.0);
                    assertThat(json).extractingPath("$[1].occasionalExpense").asNumber().isEqualTo(200.0);
                    assertThat(json).extractingPath("$[1].balance").asNumber().isEqualTo(500.0);
                    assertThat(json).extractingPath("$[0].invested").asNumber().isEqualTo(0);
                });
    }

    @Test
    void groupInvestmentsAddToTheReserve() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String finance = "/api/groups/" + groupId + "/finance";
        String invest = JsonPath.read(post(alice, "/api/finance/categories", """
                {"name":"Investimento","type":"EXPENSE","color":"#008300"}"""), "$.id");
        // Fixo de 100 desde 2 meses atrás (3 meses até agora) + ocasional de 300 neste mês + um do mês que vem (fora).
        post(alice, "/api/finance/recurring", """
                {"description":"Aporte","type":"EXPENSE","categoryId":"%s","amount":100,"startMonth":"%s","groupIds":["%s"]}
                """.formatted(invest, now.minusMonths(2), groupId));
        post(alice, finance + "/transactions", """
                {"description":"CDB","type":"EXPENSE","categoryId":"%s","amount":300,"date":"%s"}
                """.formatted(invest, now.atDay(3)));
        post(alice, finance + "/transactions", """
                {"description":"CDB","type":"EXPENSE","categoryId":"%s","amount":50,"date":"%s"}
                """.formatted(invest, now.plusMonths(1).atDay(3)));
        post(alice, finance + "/transactions", """
                {"description":"Mercado","type":"EXPENSE","categoryId":"%s","amount":80,"date":"%s"}
                """.formatted(categoryId(alice, "EXPENSE", "Alimentação"), now.atDay(3)));
        assertThat(send("PUT", alice, "/api/groups/" + groupId + "/planning/settings", """
                {"reserve":1000,"averageMonths":0}""")).bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.invested").asNumber().isEqualTo(600.0);
                    assertThat(json).extractingPath("$.totalReserve").asNumber().isEqualTo(1600.0);
                });

        assertThat(send("GET", alice, finance + "/summary?month=" + now, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.total.expense").asNumber().isEqualTo(80.0);
            assertThat(json).extractingPath("$.total.invested").asNumber().isEqualTo(400.0);
            assertThat(json).extractingPath("$.reserve.manual").asNumber().isEqualTo(1000.0);
            assertThat(json).extractingPath("$.reserve.invested").asNumber().isEqualTo(600.0);
            assertThat(json).extractingPath("$.reserve.total").asNumber().isEqualTo(1600.0);
            assertThat(json).extractingPath("$.members[0].invested").asNumber().isEqualTo(400.0);
            assertThat(json).extractingPath("$.expensesByCategory.length()").isEqualTo(1);
        });
        assertThat(send("GET", alice, "/api/groups/" + groupId + "/planning", null))
                .bodyJson().extractingPath("$.settings.totalReserve").asNumber().isEqualTo(1600.0);
        assertThat(send("GET", bob, finance + "/summary?month=" + now, null)).hasStatus(404);
    }
}
