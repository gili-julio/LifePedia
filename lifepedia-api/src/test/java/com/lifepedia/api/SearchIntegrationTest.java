package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SearchIntegrationTest extends ApiTestSupport {

    private final String day = LocalDate.now().withDayOfMonth(1).toString();

    @Test
    void findsAcrossModulesIgnoringAccentsAndTypos() {
        String alice = registerAndGetBearer();
        String food = categoryId(alice, "EXPENSE", "Alimentação");
        post(alice, "/api/finance/transactions", transaction("Padaria Centro", food, 12));
        post(alice, "/api/finance/transactions", transaction("Supermercado Bom Preço", food, 150));
        post(alice, "/api/tasks", """
                {"title":"Comprar pão na padaria"}""");
        post(alice, "/api/notes", """
                {"title":"Receita","content":"# Pão\\nFarinha, água e fermento natural.","folderId":null,"tagIds":[],
                 "icon":null,"color":null,"taskId":null,"commitmentId":null,"template":null,"version":null}""");

        String typo = body(send("GET", alice, "/api/search?q=padaira", null));
        assertThat(JsonPath.<List<String>>read(typo, "$.groups[?(@.kind == 'TRANSACTION')].items[*].title"))
                .containsExactly("Padaria Centro");
        assertThat(JsonPath.<List<String>>read(typo, "$.groups[?(@.kind == 'TASK')].items[*].title"))
                .containsExactly("Comprar pão na padaria");
        assertThat(JsonPath.<Integer>read(typo, "$.totals.count")).isEqualTo(1);

        String accent = body(send("GET", alice, "/api/search?q=PRECO", null));
        assertThat(JsonPath.<List<String>>read(accent, "$.groups[*].items[*].title")).containsExactly("Supermercado Bom Preço");

        String note = body(send("GET", alice, "/api/search?q=fermento", null));
        assertThat(JsonPath.<List<String>>read(note, "$.groups[?(@.kind == 'NOTE')].items[*].snippet").getFirst())
                .contains("fermento natural");

        String valued = body(send("GET", alice, "/api/search?min=100", null));
        assertThat(JsonPath.<List<String>>read(valued, "$.groups[*].kind")).containsExactly("TRANSACTION");
        assertThat(JsonPath.<List<String>>read(valued, "$.groups[0].items[*].title")).containsExactly("Supermercado Bom Preço");
        assertThat(JsonPath.<Double>read(valued, "$.totals.expense")).isEqualTo(150.0);

        assertThat(JsonPath.<List<Object>>read(body(send("GET", alice, "/api/search?q=a", null)), "$.groups")).isEmpty();
        assertThat(send("GET", alice, "/api/search?min=10&max=5", null)).hasStatus(400);

        String bob = registerAndGetBearer();
        assertThat(JsonPath.<List<Object>>read(body(send("GET", bob, "/api/search?q=padaria", null)), "$.groups")).isEmpty();

        assertThat(send("PUT", alice, "/api/users/me/preferences", """
                {"sidebarIcons":false,"hiddenModules":["TASKS"]}""")).hasStatusOk();
        assertThat(JsonPath.<List<String>>read(body(send("GET", alice, "/api/search?q=padaria", null)), "$.groups[*].kind"))
                .containsExactly("TRANSACTION");
    }

    @Test
    void searchesInsideGroupOnlyForMembers() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        post(alice, "/api/groups/" + groupId + "/tasks", """
                {"title":"Trocar lâmpada"}""");
        post(alice, "/api/tasks", """
                {"title":"Trocar óleo"}""");

        String inGroup = body(send("GET", alice, "/api/search?q=trocar&groupId=" + groupId, null));
        assertThat(JsonPath.<List<String>>read(inGroup, "$.groups[*].items[*].title")).containsExactly("Trocar lâmpada");
        assertThat(JsonPath.<String>read(inGroup, "$.groups[0].items[0].groupName")).isEqualTo("Casa");
        String everywhere = body(send("GET", alice, "/api/search?q=trocar", null));
        assertThat(JsonPath.<List<String>>read(everywhere, "$.groups[*].items[*].title"))
                .containsExactlyInAnyOrder("Trocar lâmpada", "Trocar óleo");

        String food = categoryId(alice, "EXPENSE", "Alimentação");
        post(alice, "/api/finance/transactions", """
                {"description":"Feira da semana","type":"EXPENSE","categoryId":"%s","amount":80,"date":"%s",
                 "groupIds":["%s"],"shareMode":"SYNC"}""".formatted(food, day, groupId));
        String month = YearMonth.now().toString();
        assertThat(send("GET", alice, "/api/groups/" + groupId + "/finance/transactions?month=" + month + "&q=FEIRA", null))
                .bodyJson().extractingPath("$.totalItems").isEqualTo(1);
        assertThat(send("GET", alice, "/api/groups/" + groupId + "/finance/transactions?month=" + month + "&q=mercado", null))
                .bodyJson().extractingPath("$.totalItems").isEqualTo(0);
        assertThat(JsonPath.<List<String>>read(body(send("GET", alice, "/api/search?q=feira&groupId=" + groupId, null)),
                "$.groups[*].kind")).containsExactly("GROUP_TRANSACTION");

        assertThat(send("GET", bob, "/api/search?q=trocar&groupId=" + groupId, null)).hasStatus(404);
        assertThat(JsonPath.<List<Object>>read(body(send("GET", bob, "/api/search?q=lampada", null)), "$.groups")).isEmpty();
    }

    @Test
    void listSearchSumsAndChangesInBatch() {
        String alice = registerAndGetBearer();
        String food = categoryId(alice, "EXPENSE", "Alimentação");
        String home = categoryId(alice, "EXPENSE", "Moradia");
        String salary = categoryId(alice, "INCOME", "Salário");
        String first = JsonPath.read(post(alice, "/api/finance/transactions", transaction("Uber Centro", food, 20)), "$.id");
        String second = JsonPath.read(post(alice, "/api/finance/transactions", transaction("Uber Aeroporto", food, 55.5)), "$.id");
        String income = JsonPath.read(post(alice, "/api/finance/transactions", """
                {"description":"Reembolso Uber","type":"INCOME","categoryId":"%s","amount":10,"date":"%s"}"""
                .formatted(salary, day)), "$.id");

        assertThat(send("GET", alice, "/api/finance/transactions/search?q=uber&size=2", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.totalItems").isEqualTo(3);
            assertThat(json).extractingPath("$.totalPages").isEqualTo(2);
            assertThat(json).extractingPath("$.items.length()").isEqualTo(2);
            assertThat(json).extractingPath("$.expense").isEqualTo(75.5);
            assertThat(json).extractingPath("$.income").isEqualTo(10.0);
        });

        String next = YearMonth.now().plusMonths(1).toString();
        assertThat(send("PUT", alice, "/api/finance/transactions/batch", """
                {"ids":["%s","%s"],"categoryId":"%s","month":"%s"}""".formatted(first, second, home, next)))
                .bodyJson().extractingPath("$.count").isEqualTo(2);
        assertThat(send("GET", alice, "/api/finance/transactions/search?categoryId=" + home, null))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.totalItems").isEqualTo(2);
                    assertThat(json).extractingPath("$.items[0].month").isEqualTo(next);
                });
        assertThat(send("PUT", alice, "/api/finance/transactions/batch", """
                {"ids":["%s","%s"],"categoryId":"%s"}""".formatted(first, income, home))).hasStatus(400);
        assertThat(send("PUT", alice, "/api/finance/transactions/batch", """
                {"ids":["%s"]}""".formatted(first))).hasStatus(400);

        String bob = registerAndGetBearer();
        assertThat(send("POST", bob, "/api/finance/transactions/batch/delete", """
                {"ids":["%s"]}""".formatted(first))).hasStatus(404);
        assertThat(send("POST", alice, "/api/finance/transactions/batch/delete", """
                {"ids":["%s","%s"]}""".formatted(first, second))).hasStatus(204);
        assertThat(send("GET", alice, "/api/finance/transactions/search?q=uber", null))
                .bodyJson().extractingPath("$.totalItems").isEqualTo(1);
    }

    @Test
    void filtersRecurringListByText() {
        String alice = registerAndGetBearer();
        String home = categoryId(alice, "EXPENSE", "Moradia");
        String month = YearMonth.now().toString();
        for (String name : List.of("Aluguel apartamento", "Conta de luz")) {
            post(alice, "/api/finance/recurring", """
                    {"description":"%s","type":"EXPENSE","categoryId":"%s","amount":100,"startMonth":"%s"}"""
                    .formatted(name, home, month));
        }
        assertThat(send("GET", alice, "/api/finance/recurring?type=EXPENSE&q=APARTAMENTO", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.totalItems").isEqualTo(1);
            assertThat(json).extractingPath("$.items[0].description").isEqualTo("Aluguel apartamento");
        });
        assertThat(send("GET", alice, "/api/finance/recurring?type=EXPENSE&q=moradia", null))
                .bodyJson().extractingPath("$.totalItems").isEqualTo(2);
        assertThat(send("GET", alice, "/api/finance/recurring?type=EXPENSE&q=50%25", null))
                .bodyJson().extractingPath("$.totalItems").isEqualTo(0);
    }

    private String transaction(String description, String category, double amount) {
        return """
                {"description":"%s","type":"EXPENSE","categoryId":"%s","amount":%s,"date":"%s"}"""
                .formatted(description, category, amount, day);
    }
}
