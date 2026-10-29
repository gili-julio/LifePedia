package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FinanceFlowIntegrationTest extends ApiTestSupport {

    @Test
    void registerLoginAndBuildMonthlySummary() {
        String email = "user-" + UUID.randomUUID() + "@lifepedia.dev";
        assertThat(mvc.post().uri("/api/auth/register").header(CSRF, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Maria","username":"%s","email":"%s","password":"segredo123","acceptTerms":true}
                        """.formatted(newUsername(), email)))
                .hasStatus(201);

        MvcTestResult login = mvc.post().uri("/api/auth/login").header(CSRF, "test").header("X-Auth-Mode", "token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"segredo123"}
                        """.formatted(email.toUpperCase()))
                .exchange();
        assertThat(login).hasStatusOk();
        String bearer = "Bearer " + JsonPath.read(body(login), "$.token");

        String salary = categoryId(bearer, "INCOME", "Salário");
        String freelance = categoryId(bearer, "INCOME", "Freelance / extra");
        String housing = categoryId(bearer, "EXPENSE", "Moradia");
        String health = categoryId(bearer, "EXPENSE", "Saúde");
        String food = categoryId(bearer, "EXPENSE", "Alimentação");

        YearMonth month = YearMonth.now();
        post(bearer, "/api/finance/recurring", """
                {"description":"Salário","type":"INCOME","categoryId":"%s","amount":5000.00,"dayOfMonth":5}
                """.formatted(salary));
        post(bearer, "/api/finance/recurring", """
                {"description":"Aluguel","type":"EXPENSE","categoryId":"%s","amount":1500.00,"dayOfMonth":10}
                """.formatted(housing));
        post(bearer, "/api/finance/recurring", """
                {"description":"Academia antiga","type":"EXPENSE","categoryId":"%s","amount":100.00,"active":false}
                """.formatted(health));
        post(bearer, "/api/finance/transactions", """
                {"description":"Mercado","type":"EXPENSE","categoryId":"%s","amount":350.50,"date":"%s"}
                """.formatted(food, month.atDay(1)));
        post(bearer, "/api/finance/transactions", """
                {"description":"Freela","type":"INCOME","categoryId":"%s","amount":800.00,"date":"%s"}
                """.formatted(freelance, month.atDay(2)));

        assertThat(send("GET", bearer, "/api/finance/summary?month=" + month, null))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.fixed.income").asNumber().isEqualTo(5000.0);
                    assertThat(json).extractingPath("$.fixed.expense").asNumber().isEqualTo(1500.0);
                    assertThat(json).extractingPath("$.occasional.expense").asNumber().isEqualTo(350.5);
                    assertThat(json).extractingPath("$.total.balance").asNumber().isEqualTo(3949.5);
                    assertThat(json).extractingPath("$.fixedCommitmentPercent").asNumber().isEqualTo(30.0);
                    assertThat(json).extractingPath("$.expensesByCategory[0].name").isEqualTo("Moradia");
                    assertThat(json).extractingPath("$.expensesByCategory[0].color").isEqualTo("#2a78d6");
                });

        assertThat(send("GET", bearer, "/api/finance/summary/history?months=3", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.length()").isEqualTo(3);
    }

    @Test
    void rejectsCategoryThatDoesNotMatchType() {
        String bearer = registerAndGetBearer();
        String housing = categoryId(bearer, "EXPENSE", "Moradia");
        assertThat(send("POST", bearer, "/api/finance/recurring", """
                {"description":"Errado","type":"INCOME","categoryId":"%s","amount":10}
                """.formatted(housing)))
                .hasStatus(400);
    }

    @Test
    void usersCannotSeeEachOthersEntriesOrUseTheirCategories() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String aliceUtilities = categoryId(alice, "EXPENSE", "Contas da casa");
        String body = post(alice, "/api/finance/recurring", """
                {"description":"Internet","type":"EXPENSE","categoryId":"%s","amount":120}
                """.formatted(aliceUtilities));
        String id = JsonPath.read(body, "$.id");

        assertThat(send("DELETE", bob, "/api/finance/recurring/" + id, null)).hasStatus(404);
        assertThat(send("GET", bob, "/api/finance/recurring/all", null))
                .hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(0);
        assertThat(send("POST", bob, "/api/finance/transactions", """
                {"description":"x","type":"EXPENSE","categoryId":"%s","amount":1,"date":"2026-01-01"}
                """.formatted(aliceUtilities)))
                .hasStatus(400);
    }

    @Test
    void rejectsRequestsWithoutToken() {
        assertThat(mvc.get().uri("/api/finance/summary")).hasStatus(401);
    }

    @Test
    void rejectsDuplicatedEmail() {
        String email = "dup-" + UUID.randomUUID() + "@lifepedia.dev";
        String payload = """
                {"name":"Ana","username":"%s","email":"%s","password":"segredo123","acceptTerms":true}
                """.formatted(newUsername(), email);
        assertThat(mvc.post().uri("/api/auth/register").header(CSRF, "test").contentType(MediaType.APPLICATION_JSON).content(payload))
                .hasStatus(201);
        assertThat(mvc.post().uri("/api/auth/register").header(CSRF, "test").contentType(MediaType.APPLICATION_JSON).content(payload))
                .hasStatus(409);
    }

    @Test
    void rejectsTamperedToken() {
        String bearer = registerAndGetBearer();
        String tampered = bearer.substring(0, bearer.length() - 4) + "abcd";
        assertThat(mvc.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, tampered)).hasStatus(401);
    }
}
