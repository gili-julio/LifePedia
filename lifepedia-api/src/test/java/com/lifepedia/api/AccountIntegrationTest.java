package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccountIntegrationTest extends ApiTestSupport {

    private final String month = YearMonth.now().toString();
    private final String day = YearMonth.now().atDay(1).toString();

    @Test
    void exportAndImportIntoAnotherAccount() throws IOException {
        String alice = registerAndGetBearer("Alice", newUsername());
        String pets = JsonPath.read(post(alice, "/api/finance/categories", """
                {"name":"Pets","type":"EXPENSE","color":"#AA3300"}"""), "$.id");
        post(alice, "/api/finance/transactions", """
                {"description":"Mercado","type":"EXPENSE","categoryId":"%s","amount":120.50,"date":"%s",
                 "originalDescription":"MERCADO XYZ 123"}""".formatted(categoryId(alice, "EXPENSE", "Alimentação"), day));
        post(alice, "/api/finance/transactions", """
                {"description":"Ração","type":"EXPENSE","categoryId":"%s","amount":89.90,"date":"%s"}""".formatted(pets, day));
        String rent = JsonPath.read(post(alice, "/api/finance/recurring", """
                {"description":"Aluguel","type":"EXPENSE","categoryId":"%s","amount":1500,"startMonth":"%s"}"""
                .formatted(categoryId(alice, "EXPENSE", "Moradia"), month)), "$.id");
        assertThat(send("PUT", alice, "/api/finance/recurring/" + rent + "/adjustments/" + month, """
                {"amount":1600}""")).hasStatusOk();

        MvcTestResult exported = send("GET", alice, "/api/account/export", null);
        assertThat(exported).hasStatusOk();
        assertThat(exported.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment", ".json");
        String file = body(exported);
        assertThat(exported).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.format").isEqualTo("lifepedia/account@2");
            assertThat(json).extractingPath("$.profile.name").isEqualTo("Alice");
            assertThat(json).extractingPath("$.transactions.length()").isEqualTo(2);
            assertThat(json).extractingPath("$.recurring[0].adjustments[0].amount").asNumber().isEqualTo(1600.0);
            assertThat(json).extractingPath("$.transactions[0].originalDescription").isEqualTo("MERCADO XYZ 123");
        });

        MvcTestResult sheet = send("GET", alice, "/api/account/export/spreadsheet", null);
        assertThat(sheet).hasStatusOk();
        assertThat(sheet.getResponse().getContentType()).contains("spreadsheetml");
        Map<String, String> parts = unzip(sheet.getResponse().getContentAsByteArray());
        assertThat(parts).containsKeys("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet5.xml");
        assertThat(parts.get("xl/worksheets/sheet1.xml")).contains("Mercado", "MERCADO XYZ 123", "120.50");
        assertThat(parts.get("xl/workbook.xml")).contains("Ajustes dos fixos");

        // Outra conta: prévia não grava; depois importa; de novo não duplica.
        String bob = registerAndGetBearer();
        String merge = "{\"mode\":\"MERGE\",\"dryRun\":true,\"data\":" + file + "}";
        assertThat(send("POST", bob, "/api/account/import", merge)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.applied").isEqualTo(false);
            assertThat(json).extractingPath("$.created.transactions").isEqualTo(2);
            assertThat(json).extractingPath("$.created.categories").isEqualTo(1);
            assertThat(json).extractingPath("$.created.adjustments").isEqualTo(1);
        });
        assertThat(send("GET", bob, "/api/account/export", null)).bodyJson()
                .extractingPath("$.transactions.length()").isEqualTo(0);

        assertThat(send("POST", bob, "/api/account/import", merge.replace("\"dryRun\":true", "\"dryRun\":false")))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.applied").isEqualTo(true);
                    assertThat(json).extractingPath("$.created.recurring").isEqualTo(1);
                    assertThat(json).extractingPath("$.categoriesReused").isEqualTo(19);
                });
        String bobPets = categoryId(bob, "EXPENSE", "Pets");
        assertThat(send("GET", bob, "/api/finance/transactions?month=" + month, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.totalItems").isEqualTo(2);
            assertThat(json).extractingPath("$.items[?(@.description == 'Ração')].categoryId").asArray().containsExactly(bobPets);
        });
        assertThat(send("GET", bob, "/api/finance/recurring/all", null)).bodyJson()
                .extractingPath("$[0].adjustments[0].amount").asNumber().isEqualTo(1600.0);

        assertThat(send("POST", bob, "/api/account/import", merge.replace("\"dryRun\":true", "\"dryRun\":false")))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.created.transactions").isEqualTo(0);
                    assertThat(json).extractingPath("$.skipped.transactions").isEqualTo(2);
                    assertThat(json).extractingPath("$.skipped.recurring").isEqualTo(1);
                    assertThat(json).extractingPath("$.skipped.adjustments").isEqualTo(1);
                });

        // Substituir: apaga o pessoal e traz só o do arquivo; categoria sem uso sai.
        String replace = """
                {"mode":"REPLACE","data":{"format":"lifepedia/account@1","categories":[
                  {"ref":"c1","name":"Moradia","type":"EXPENSE","color":"#2a78d6"},
                  {"ref":"c2","name":"Salário","type":"INCOME","color":"#2a78d6"}],
                 "recurring":[],
                 "transactions":[{"description":"Conta","type":"EXPENSE","category":"c1","amount":10,"date":"%s","month":"%s"}]}}"""
                .formatted(day, month);
        assertThat(send("POST", bob, "/api/account/import", replace)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.removed.transactions").isEqualTo(2);
            assertThat(json).extractingPath("$.removed.recurring").isEqualTo(1);
            assertThat(json).extractingPath("$.removed.categories").isEqualTo(18);
            assertThat(json).extractingPath("$.created.transactions").isEqualTo(1);
        });
        assertThat(send("GET", bob, "/api/account/export", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.transactions.length()").isEqualTo(1);
            assertThat(json).extractingPath("$.recurring.length()").isEqualTo(0);
            assertThat(json).extractingPath("$.categories.length()").isEqualTo(2);
        });

        // Arquivo inválido: nada muda.
        assertThat(send("POST", bob, "/api/account/import", replace.replace("account@1", "account@9"))).hasStatus(400);
        assertThat(send("POST", bob, "/api/account/import", replace.replace("\"category\":\"c1\"", "\"category\":\"x\"")))
                .hasStatus(400);
        assertThat(send("POST", bob, "/api/account/import", replace.replace("\"category\":\"c1\"", "\"category\":\"c2\"")))
                .hasStatus(400);
        assertThat(send("POST", bob, "/api/account/import",
                replace.replace("\"month\":\"" + month, "\"month\":\"" + YearMonth.now().minusMonths(1)))).hasStatus(400);
        assertThat(send("POST", bob, "/api/account/import", replace.replace("\"amount\":10", "\"amount\":-1")))
                .hasStatus(400);
        assertThat(send("POST", bob, "/api/account/import", "{\"mode\":\"MERGE\"}")).hasStatus(400);
        assertThat(send("GET", bob, "/api/account/export", null)).bodyJson()
                .extractingPath("$.transactions.length()").isEqualTo(1);
    }

    @Test
    void exportAndImportTasks() {
        String alice = registerAndGetBearer();
        String fixed = JsonPath.read(post(alice, "/api/finance/recurring", """
                {"description":"Luz","type":"EXPENSE","categoryId":"%s","amount":180,"startMonth":"%s"}"""
                .formatted(categoryId(alice, "EXPENSE", "Moradia"), month)), "$.id");
        String list = JsonPath.read(post(alice, "/api/tasks/lists", """
                {"name":"Casa","color":"#2a78d6"}"""), "$.id");
        String tag = JsonPath.read(post(alice, "/api/tasks/tags", """
                {"name":"contas","color":"#e34948"}"""), "$.id");
        String bill = body(send("POST", alice, "/api/tasks", """
                {"title":"Pagar luz","dueDate":"%s","dueTime":"09:00","reminderMinutes":60,"listId":"%s","tagIds":["%s"],
                 "habit":true,"repeat":{"unit":"WEEK","interval":1},"recurringEntryId":"%s",
                 "subtasks":[{"title":"Conferir"},{"title":"Pagar"}]}""".formatted(day, list, tag, fixed)));
        String billId = JsonPath.read(bill, "$.id");
        String check = JsonPath.read(bill, "$.subtasks[1].id");
        assertThat(send("PUT", alice, "/api/tasks/" + billId + "/subtasks/" + check + "/check", """
                {"date":"%s","done":true}""".formatted(day))).hasStatus(204);
        assertThat(send("PUT", alice, "/api/tasks/" + billId + "/status", """
                {"date":"%s","status":"DONE"}""".formatted(day))).hasStatus(204);
        post(alice, "/api/tasks", """
                {"title":"Sem dia"}""");

        String file = body(send("GET", alice, "/api/account/export", null));
        assertThat(JsonPath.<Object>read(file, "$.tasks.length()")).isEqualTo(2);
        assertThat(JsonPath.<String>read(file, "$.tasks[0].fixed")).isEqualTo(JsonPath.read(file, "$.recurring[0].ref"));
        assertThat(JsonPath.<List<Integer>>read(file, "$.tasks[0].occurrences[0].checked")).containsExactly(1);

        String bob = registerAndGetBearer();
        String merge = "{\"mode\":\"MERGE\",\"dryRun\":%s,\"data\":" + file + "}";
        assertThat(send("POST", bob, "/api/account/import", merge.formatted(true))).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.created.tasks").isEqualTo(2);
            assertThat(json).extractingPath("$.created.taskLabels").isEqualTo(2);
        });
        assertThat(send("POST", bob, "/api/account/import", merge.formatted(false))).hasStatusOk();
        String mine = body(send("GET", bob, "/api/account/export", null));
        assertThat(JsonPath.<Object>read(mine, "$.tasks.length()")).isEqualTo(2);
        assertThat(JsonPath.<String>read(mine, "$.tasks[0].fixed")).isEqualTo(JsonPath.read(mine, "$.recurring[0].ref"));
        assertThat(JsonPath.<String>read(mine, "$.tasks[0].occurrences[0].status")).isEqualTo("DONE");
        assertThat(JsonPath.<List<Integer>>read(mine, "$.tasks[0].occurrences[0].checked")).containsExactly(1);
        assertThat(JsonPath.<String>read(mine, "$.taskLists[0].name")).isEqualTo("Casa");
        assertThat(JsonPath.<Integer>read(mine, "$.tasks[0].reminderMinutes")).isEqualTo(60);

        // De novo não duplica; @1 (sem tarefas) não mexe nelas; @2 com lista vazia apaga.
        assertThat(send("POST", bob, "/api/account/import", merge.formatted(false))).bodyJson()
                .extractingPath("$.skipped.tasks").isEqualTo(2);
        String empty = """
                {"mode":"REPLACE","data":{"format":"lifepedia/account@%s","categories":[],"recurring":[],"transactions":[]%s}}""";
        assertThat(send("POST", bob, "/api/account/import", empty.formatted(1, ""))).bodyJson()
                .extractingPath("$.removed.tasks").isEqualTo(0);
        assertThat(send("GET", bob, "/api/account/deletion-preview", null)).bodyJson()
                .extractingPath("$.tasks").isEqualTo(2);
        assertThat(send("POST", bob, "/api/account/import", empty.formatted(2, ",\"tasks\":[]"))).bodyJson()
                .extractingPath("$.removed.tasks").isEqualTo(2);

        // Ref desconhecida ou hábito sem repetição: 400.
        assertThat(send("POST", bob, "/api/account/import", empty.formatted(2, ",\"tasks\":[{\"title\":\"X\",\"list\":\"nope\"}]")))
                .hasStatus(400);
        assertThat(send("POST", bob, "/api/account/import", empty.formatted(2, ",\"tasks\":[{\"title\":\"X\",\"habit\":true}]")))
                .hasStatus(400);
    }

    @Test
    void replaceKeepsSyncedGroupItemsAsCopies() {
        String alice = registerAndGetBearer();
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String food = categoryId(alice, "EXPENSE", "Alimentação");
        post(alice, "/api/finance/transactions", """
                {"description":"Feira","type":"EXPENSE","categoryId":"%s","amount":50,"date":"%s",
                 "groupIds":["%s"],"shareMode":"SYNC"}""".formatted(food, day, groupId));

        String replace = """
                {"mode":"REPLACE","data":{"format":"lifepedia/account@1","categories":[],"recurring":[],"transactions":[]}}""";
        assertThat(send("POST", alice, "/api/account/import", replace)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.syncedDetached").isEqualTo(1);
            assertThat(json).extractingPath("$.removed.transactions").isEqualTo(1);
        });
        assertThat(send("GET", alice, "/api/groups/" + groupId + "/finance/transactions?month=" + month, null))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.totalItems").isEqualTo(1);
                    assertThat(json).extractingPath("$.items[0].synced").isEqualTo(false);
                });
        // A categoria usada no grupo continua.
        assertThat(categoryId(alice, "EXPENSE", "Alimentação")).isEqualTo(food);
    }

    @Test
    void deleteAccountLeavesGroupsAndNotifiesMembers() {
        String alice = registerAndGetBearer("Alice Souza", newUsername());
        String bob = registerAndGetBearer("Bob", newUsername());
        String bobId = userId(bob);

        String shared = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        join(alice, bob, shared);
        String alone = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Só eu"}"""), "$.id");
        String bobs = JsonPath.read(post(bob, "/api/groups", """
                {"name":"Do Bob"}"""), "$.id");
        join(bob, alice, bobs);

        post(alice, "/api/finance/transactions", """
                {"description":"Feira","type":"EXPENSE","categoryId":"%s","amount":50,"date":"%s",
                 "groupIds":["%s"],"shareMode":"SYNC"}""".formatted(categoryId(alice, "EXPENSE", "Alimentação"), day, shared));
        post(alice, "/api/groups/" + shared + "/planning/items", """
                {"name":"Sofá","kind":"PURCHASE","amount":1000,"targetMonth":"%s"}""".formatted(YearMonth.now().plusMonths(3)));

        assertThat(send("GET", alice, "/api/account/deletion-preview", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.transactions").isEqualTo(1);
            assertThat(json).extractingPath("$.groups.length()").isEqualTo(3);
            assertThat(json).extractingPath("$.groups[?(@.name == 'Casa')].newAdmin.id").asArray().containsExactly(bobId);
            assertThat(json).extractingPath("$.groups[?(@.name == 'Casa')].transactions").asArray().containsExactly(1);
            assertThat(json).extractingPath("$.groups[?(@.name == 'Casa')].planItems").asArray().containsExactly(1);
            assertThat(json).extractingPath("$.groups[?(@.name == 'Só eu')].deletesGroup").asArray().containsExactly(true);
            assertThat(json).extractingPath("$.groups[?(@.name == 'Do Bob')].newAdmin").asArray().containsExactly((Object) null);
        });

        assertThat(send("DELETE", alice, "/api/account", """
                {"password":"errada","confirmation":"EXCLUIR"}""")).hasStatus(400);
        assertThat(send("DELETE", alice, "/api/account", """
                {"password":"segredo123","confirmation":"excluir"}""")).hasStatus(400);
        assertThat(send("GET", alice, "/api/users/me", null)).hasStatusOk();

        MvcTestResult deleted = send("DELETE", alice, "/api/account", """
                {"password":"segredo123","confirmation":"EXCLUIR"}""");
        assertThat(deleted).hasStatus(204);
        assertThat(deleted.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        // Token antigo não vale mais.
        assertThat(send("GET", alice, "/api/users/me", null)).hasStatus(401);

        assertThat(send("GET", bob, "/api/groups/" + shared, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.role").isEqualTo("ADMIN");
            assertThat(json).extractingPath("$.members.length()").isEqualTo(1);
        });
        assertThat(send("GET", bob, "/api/groups/" + shared + "/finance/transactions?month=" + month, null))
                .bodyJson().extractingPath("$.totalItems").isEqualTo(0);
        assertThat(send("GET", bob, "/api/groups/" + shared + "/planning", null))
                .bodyJson().extractingPath("$.items.length()").isEqualTo(0);
        assertThat(send("GET", bob, "/api/groups/" + bobs, null))
                .bodyJson().extractingPath("$.members.length()").isEqualTo(1);
        assertThat(send("GET", bob, "/api/notifications", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items[?(@.type == 'MEMBER_ACCOUNT_DELETED')].data.groupName").asArray()
                    .containsExactlyInAnyOrder("Casa", "Do Bob");
            assertThat(json).extractingPath("$.items[?(@.data.groupName == 'Casa')].data.promoted").asArray()
                    .containsExactly("true");
            assertThat(json).extractingPath("$.items[?(@.data.groupName == 'Do Bob')].data.actorName").asArray()
                    .containsExactly("Alice Souza");
        });
    }

    private void join(String admin, String member, String groupId) {
        String token = JsonPath.read(body(send("POST", admin, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", member, "/api/groups/join/" + token, null)).hasStatusOk();
    }

    private static Map<String, String> unzip(byte[] bytes) throws IOException {
        Map<String, String> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                parts.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return parts;
    }
}
