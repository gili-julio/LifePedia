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
class ListingIntegrationTest extends ApiTestSupport {

    private static final String MONTH = "2026-09";

    private String createTransaction(String bearer, String category, String description, double amount, String date) {
        return JsonPath.read(post(bearer, "/api/finance/transactions", """
                {"description":"%s","type":"EXPENSE","categoryId":"%s","amount":%s,"date":"%s"}
                """.formatted(description, category, amount, date)), "$.id");
    }

    @Test
    void transactionsArePagedAndSortedByTheApi() {
        String bearer = registerAndGetBearer();
        String food = categoryId(bearer, "EXPENSE", "Alimentação");
        String home = categoryId(bearer, "EXPENSE", "Moradia");
        for (int day = 1; day <= 12; day++) {
            createTransaction(bearer, day % 2 == 0 ? food : home, "Item " + day, day * 10, "2026-09-%02d".formatted(day));
        }

        assertThat(send("GET", bearer, "/api/finance/transactions?month=" + MONTH, null))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.items.length()").isEqualTo(10);
                    assertThat(json).extractingPath("$.totalItems").isEqualTo(12);
                    assertThat(json).extractingPath("$.totalPages").isEqualTo(2);
                    assertThat(json).extractingPath("$.items[0].date").isEqualTo("2026-09-12");
                });
        assertThat(send("GET", bearer, "/api/finance/transactions?month=" + MONTH + "&page=1&size=10&sort=amount,asc", null))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.items.length()").isEqualTo(2);
                    assertThat(json).extractingPath("$.items[0].amount").asNumber().isEqualTo(110.0);
                });
        List<String> categories = JsonPath.read(body(send("GET", bearer,
                "/api/finance/transactions?month=" + MONTH + "&size=25&sort=category,asc", null)), "$.items[*].categoryId");
        assertThat(categories.subList(0, 6)).containsOnly(food);

        // totais do topo continuam considerando todos os itens
        assertThat(send("GET", bearer, "/api/finance/summary?month=" + MONTH, null))
                .bodyJson().extractingPath("$.occasional.expense").asNumber().isEqualTo(780.0);
    }

    @Test
    void rejectsUnknownSortAndInvalidSize() {
        String bearer = registerAndGetBearer();
        assertThat(send("GET", bearer, "/api/finance/transactions?sort=userId,asc", null)).hasStatus(400);
        assertThat(send("GET", bearer, "/api/finance/recurring?sort=amount,sideways", null)).hasStatus(400);
        assertThat(send("GET", bearer, "/api/finance/categories?size=500", null)).hasStatus(400);
    }

    @Test
    void recurringAndCategoriesArePaged() {
        String bearer = registerAndGetBearer();
        String bills = categoryId(bearer, "EXPENSE", "Contas da casa");
        for (int i = 1; i <= 3; i++) {
            post(bearer, "/api/finance/recurring", """
                    {"description":"Fixo %d","type":"EXPENSE","categoryId":"%s","amount":%d,"startMonth":"%s"}
                    """.formatted(i, bills, i * 100, MONTH));
        }
        assertThat(send("GET", bearer, "/api/finance/recurring?type=EXPENSE&size=2&sort=description,desc", null))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.totalItems").isEqualTo(3);
                    assertThat(json).extractingPath("$.items[0].description").isEqualTo("Fixo 3");
                });

        createTransaction(bearer, bills, "Luz", 50, "2026-09-03");
        assertThat(send("GET", bearer, "/api/finance/categories?type=EXPENSE&sort=usage,desc", null))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.items[0].name").isEqualTo("Contas da casa");
                    assertThat(json).extractingPath("$.items[0].usageCount").isEqualTo(4);
                });
    }

    @Test
    void creatingFixedRemovesTheOccasionalsItReplaces() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String subscriptions = categoryId(alice, "EXPENSE", "Assinaturas");
        String aug = createTransaction(alice, subscriptions, "Spotify", 21.9, "2026-08-10");
        String sep = createTransaction(alice, subscriptions, "SPOTIFY ", 21.9, "2026-09-10");
        createTransaction(alice, subscriptions, "Netflix", 55.9, "2026-09-12");

        List<String> similar = JsonPath.read(body(send("GET", alice,
                "/api/finance/transactions/all?from=2026-08&type=EXPENSE&description=spotify", null)), "$[*].id");
        assertThat(similar).containsExactlyInAnyOrder(aug, sep);

        String request = """
                {"description":"Spotify","type":"EXPENSE","categoryId":"%s","amount":21.9,"startMonth":"2026-08",
                 "removeTransactionIds":["%s","%s"]}
                """;
        assertThat(send("POST", bob, "/api/finance/recurring",
                request.formatted(categoryId(bob, "EXPENSE", "Assinaturas"), aug, sep))).hasStatus(404);
        assertThat(send("GET", bob, "/api/finance/recurring/all", null)).bodyJson().extractingPath("$.length()").isEqualTo(0);
        post(alice, "/api/finance/recurring", request.formatted(subscriptions, aug, sep));

        assertThat(send("GET", alice, "/api/finance/transactions/all?from=2026-08", null))
                .bodyJson().extractingPath("$[*].description").asArray().containsExactly("Netflix");
    }

    @Test
    void categoryItemsGroupOccasionalsByDescription() {
        String bearer = registerAndGetBearer();
        String food = categoryId(bearer, "EXPENSE", "Alimentação");
        createTransaction(bearer, food, "Lanche IMD", 4, "2026-09-02");
        createTransaction(bearer, food, "LANCHE IMD", 6, "2026-09-09");
        createTransaction(bearer, food, "Mercado", 200, "2026-09-10");
        post(bearer, "/api/finance/recurring", """
                {"description":"Marmita","type":"EXPENSE","categoryId":"%s","amount":300,"startMonth":"%s"}
                """.formatted(food, MONTH));

        assertThat(send("GET", bearer, "/api/finance/summary/categories/" + food + "?month=" + MONTH, null))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.length()").isEqualTo(3);
                    assertThat(json).extractingPath("$[0].description").isEqualTo("Marmita");
                    assertThat(json).extractingPath("$[0].fixed").isEqualTo(true);
                    assertThat(json).extractingPath("$[2].count").isEqualTo(2);
                    assertThat(json).extractingPath("$[2].total").asNumber().isEqualTo(10.0);
                });
        assertThat(send("GET", registerAndGetBearer(), "/api/finance/summary/categories/" + food, null)).hasStatus(404);
    }
}
