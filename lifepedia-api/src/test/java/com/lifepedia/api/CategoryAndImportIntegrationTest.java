package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CategoryAndImportIntegrationTest extends ApiTestSupport {

    @Test
    void newUserGetsDefaultCategories() {
        String bearer = registerAndGetBearer();
        assertThat(send("GET", bearer, "/api/finance/categories/all?type=EXPENSE", null))
                .hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(13);
        assertThat(send("GET", bearer, "/api/finance/categories/all?type=INCOME", null))
                .hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(6);
    }

    @Test
    void createUpdateAndRejectDuplicatedName() {
        String bearer = registerAndGetBearer();
        String body = post(bearer, "/api/finance/categories", """
                {"name":"Pets","type":"EXPENSE","color":"#AABBCC"}
                """);
        String id = JsonPath.read(body, "$.id");
        assertThat((String) JsonPath.read(body, "$.color")).isEqualTo("#aabbcc");

        assertThat(send("PUT", bearer, "/api/finance/categories/" + id, """
                {"name":"Pets e veterinário","type":"EXPENSE","color":"#112233"}
                """)).hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("Pets e veterinário");

        assertThat(send("POST", bearer, "/api/finance/categories", """
                {"name":"moradia","type":"EXPENSE","color":"#112233"}
                """)).hasStatus(409);
        assertThat(send("POST", bearer, "/api/finance/categories", """
                {"name":"Ruim","type":"EXPENSE","color":"vermelho"}
                """)).hasStatus(400);
    }

    @Test
    void deletingCategoryInUseRequiresReplacementAndMovesEntries() {
        String bearer = registerAndGetBearer();
        String leisure = categoryId(bearer, "EXPENSE", "Lazer");
        String other = categoryId(bearer, "EXPENSE", "Outros");
        String salary = categoryId(bearer, "INCOME", "Salário");
        String tx = post(bearer, "/api/finance/transactions", """
                {"description":"Cinema","type":"EXPENSE","categoryId":"%s","amount":40,"date":"2026-09-10"}
                """.formatted(leisure));

        assertThat(send("DELETE", bearer, "/api/finance/categories/" + leisure, null)).hasStatus(409);
        assertThat(send("DELETE", bearer, "/api/finance/categories/" + leisure + "?replaceWith=" + salary, null))
                .hasStatus(400);
        assertThat(send("DELETE", bearer, "/api/finance/categories/" + leisure + "?replaceWith=" + other, null))
                .hasStatus(204);

        List<String> moved = JsonPath.read(body(send("GET", bearer, "/api/finance/transactions/all?month=2026-09", null)),
                "$[?(@.id == '" + JsonPath.read(tx, "$.id") + "')].categoryId");
        assertThat(moved).containsExactly(other);
    }

    @Test
    void cannotDeleteLastCategoryOfAType() {
        String bearer = registerAndGetBearer();
        List<String> incomeIds = JsonPath.read(body(send("GET", bearer, "/api/finance/categories/all?type=INCOME", null)), "$[*].id");
        for (int i = 0; i < incomeIds.size() - 1; i++) {
            assertThat(send("DELETE", bearer, "/api/finance/categories/" + incomeIds.get(i), null)).hasStatus(204);
        }
        assertThat(send("DELETE", bearer, "/api/finance/categories/" + incomeIds.getLast(), null)).hasStatus(400);
    }

    @Test
    void batchImportIsAllOrNothing() {
        String bearer = registerAndGetBearer();
        String food = categoryId(bearer, "EXPENSE", "Alimentação");
        String other = categoryId(bearer, "INCOME", "Outros");

        assertThat(send("POST", bearer, "/api/finance/transactions/batch", """
                {"items":[
                  {"description":"Mercado","type":"EXPENSE","categoryId":"%s","amount":100.5,"date":"2026-08-01"},
                  {"description":"Estorno","type":"INCOME","categoryId":"%s","amount":20,"date":"2026-08-02"}
                ]}
                """.formatted(food, other))).hasStatus(201).bodyJson().extractingPath("$.length()").isEqualTo(2);

        // segunda linha usa categoria de despesa num ganho -> nada é salvo
        assertThat(send("POST", bearer, "/api/finance/transactions/batch", """
                {"items":[
                  {"description":"Padaria","type":"EXPENSE","categoryId":"%s","amount":10,"date":"2026-07-01"},
                  {"description":"Errado","type":"INCOME","categoryId":"%s","amount":5,"date":"2026-07-02"}
                ]}
                """.formatted(food, food))).hasStatus(400);
        assertThat(send("GET", bearer, "/api/finance/transactions/all?month=2026-07", null))
                .hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(0);

        assertThat(send("POST", bearer, "/api/finance/transactions/batch", """
                {"items":[]}
                """)).hasStatus(400);
    }
}
