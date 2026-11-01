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
class BankIntegrationTest extends ApiTestSupport {

    private static final YearMonth MONTH = YearMonth.of(2026, 9);
    private static final String CARD = """
            {"code":"NUBANK","kind":"CARD"}""";

    @Test
    void itemsCarryBankAndReportSumsPerBank() {
        String me = registerAndGetBearer();
        String category = categoryId(me, "EXPENSE", "Moradia");
        transaction(me, category, "Compra", 100, CARD);
        transaction(me, category, "Feira", 50, """
                {"code":"NUBANK","name":"ignorado","kind":"ACCOUNT"}""");
        transaction(me, category, "Padaria", 30, """
                {"code":"OTHER","name":"Banco da Vila","kind":"ACCOUNT"}""");
        transaction(me, category, "Sem banco", 20, "null");
        fixed(me, category, "Aluguel", 200, """
                {"code":"ITAU"}""");

        String report = get(me, "/api/finance/summary/banks?month=" + MONTH);
        assertThat(number(report, "$.expense")).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(report, "$.banks[*].key"))
                .containsExactly("ITAU", "NUBANK", "OTHER:banco da vila", "NONE");
        assertThat(number(report, "$.banks[1].expense")).isEqualTo(150);
        assertThat(number(report, "$.banks[1].card")).isEqualTo(100);
        assertThat(number(report, "$.banks[1].account")).isEqualTo(50);
        assertThat(JsonPath.<String>read(report, "$.banks[2].name")).isEqualTo("Banco da Vila");
        assertThat(JsonPath.<String>read(report, "$.banks[3].name")).isEqualTo("Sem banco");
        assertThat(JsonPath.<List<Object>>read(report, "$.banks[0].history")).hasSize(6);
        assertThat(number(report, "$.banks[0].history[5]")).isEqualTo(200);
        assertThat(number(report, "$.banks[0].fixed")).isEqualTo(200);

        assertThat(count(me, "/api/finance/transactions?month=" + MONTH + "&bank=NUBANK&bankKind=CARD")).isEqualTo(1);
        assertThat(count(me, "/api/finance/transactions?month=" + MONTH + "&bank=OTHER:banco da vila")).isEqualTo(1);
        assertThat(count(me, "/api/finance/transactions?month=" + MONTH + "&bank=NONE")).isEqualTo(1);
        assertThat(count(me, "/api/finance/recurring?bank=ITAU")).isEqualTo(1);
        assertThat(number(get(me, "/api/finance/summary?month=" + MONTH + "&bank=NUBANK"), "$.total.expense")).isEqualTo(150);
        assertThat(JsonPath.<String>read(get(me, "/api/finance/transactions?month=" + MONTH + "&bank=NUBANK&bankKind=ACCOUNT"),
                "$.items[0].bank.name")).isNull();
    }

    @Test
    void rejectsUnknownBankAndOtherWithoutName() {
        String me = registerAndGetBearer();
        String category = categoryId(me, "EXPENSE", "Moradia");
        assertThat(send("POST", me, "/api/finance/transactions", body(category, "Compra", 10, """
                {"code":"NAO_EXISTE"}"""))).hasStatus(400);
        assertThat(send("POST", me, "/api/finance/transactions", body(category, "Compra", 10, """
                {"code":"OTHER","name":" "}"""))).hasStatus(400);
        assertThat(send("GET", me, "/api/finance/transactions?bank=NAO_EXISTE", null)).hasStatus(400);
    }

    @Test
    void assignsInBulkSyncsGroupAndKeepsOtherUsersOut() {
        String me = registerAndGetBearer();
        String other = registerAndGetBearer();
        String category = categoryId(me, "EXPENSE", "Moradia");
        String first = transaction(me, category, "Compra", 100, "null");
        String second = transaction(me, category, "Feira", 50, "null");
        String entry = fixed(me, category, "Aluguel", 200, "null");
        String groupId = JsonPath.read(post(me, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String base = "/api/groups/" + groupId + "/finance";
        assertThat(send("POST", me, base + "/items", """
                {"transactionIds":["%s"],"recurringIds":["%s"],"mode":"SYNC"}""".formatted(first, entry))).hasStatus2xxSuccessful();

        assertThat(send("PUT", me, "/api/finance/banks/assign", """
                {"transactionIds":["%s","%s"],"recurringIds":["%s"],"bank":{"code":"SANTANDER","kind":"CARD"}}"""
                .formatted(first, second, entry))).hasStatusOk();

        assertThat(count(me, "/api/finance/transactions?month=" + MONTH + "&bank=SANTANDER")).isEqualTo(2);
        assertThat(count(me, base + "/transactions?month=" + MONTH + "&bank=SANTANDER&bankKind=CARD")).isEqualTo(1);
        assertThat(count(me, base + "/recurring?bank=SANTANDER")).isEqualTo(1);
        assertThat(number(get(me, base + "/summary/banks?month=" + MONTH), "$.banks[0].expense")).isEqualTo(300);
        assertThat(number(get(me, base + "/summary?month=" + MONTH + "&bank=SANTANDER"), "$.total.expense")).isEqualTo(300);

        assertThat(send("PUT", me, "/api/finance/transactions/" + first, body(category, "Compra", 100, """
                {"code":"ITAU"}"""))).hasStatusOk();
        assertThat(JsonPath.<String>read(get(me, base + "/transactions?month=" + MONTH), "$.items[0].bank.code")).isEqualTo("ITAU");

        assertThat(JsonPath.<String>read(get(me, "/api/finance/banks/last"), "$.bank.code")).isEqualTo("SANTANDER");
        assertThat(send("PUT", other, "/api/finance/banks/assign", """
                {"transactionIds":["%s"],"bank":{"code":"BB"}}""".formatted(first))).hasStatus(404);
        assertThat(send("PUT", me, "/api/finance/banks/assign", """
                {"transactionIds":["%s"]}""".formatted(second))).hasStatusOk();
        assertThat(count(me, "/api/finance/transactions?month=" + MONTH + "&bank=NONE")).isEqualTo(1);
    }

    @Test
    void importKeepsBankAndFillsFixedWithoutOne() {
        String me = registerAndGetBearer();
        String category = categoryId(me, "EXPENSE", "Moradia");
        String empty = fixed(me, category, "Luz", 90, "null");
        String taken = fixed(me, category, "Água", 40, """
                {"code":"ITAU"}""");

        assertThat(send("POST", me, "/api/finance/import", """
                {"transactions":[%s],"fixedBanks":[
                  {"recurringEntryId":"%s","bank":{"code":"INTER"}},
                  {"recurringEntryId":"%s","bank":{"code":"INTER"}}]}"""
                .formatted(body(category, "Compra", 10, CARD), empty, taken))).hasStatus2xxSuccessful();

        assertThat(count(me, "/api/finance/transactions?month=" + MONTH + "&bank=NUBANK")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(get(me, "/api/finance/recurring?bank=INTER"), "$.items[*].id")).containsExactly(empty);
        assertThat(JsonPath.<List<String>>read(get(me, "/api/finance/recurring?bank=ITAU"), "$.items[*].id")).containsExactly(taken);
    }

    private String transaction(String bearer, String category, String description, int amount, String bank) {
        return JsonPath.read(post(bearer, "/api/finance/transactions", body(category, description, amount, bank)), "$.id");
    }

    private static String body(String category, String description, int amount, String bank) {
        return """
                {"description":"%s","type":"EXPENSE","categoryId":"%s","amount":%d,"date":"%s","bank":%s}"""
                .formatted(description, category, amount, MONTH.atDay(10), bank);
    }

    private String fixed(String bearer, String category, String description, int amount, String bank) {
        return JsonPath.read(post(bearer, "/api/finance/recurring", """
                {"description":"%s","type":"EXPENSE","categoryId":"%s","amount":%d,"startMonth":"%s","bank":%s}"""
                .formatted(description, category, amount, MONTH.minusMonths(1), bank)), "$.id");
    }

    private String get(String bearer, String uri) {
        var result = send("GET", bearer, uri, null);
        assertThat(result).hasStatusOk();
        return body(result);
    }

    private int count(String bearer, String uri) {
        return JsonPath.read(get(bearer, uri), "$.totalItems");
    }

    private static int number(String json, String path) {
        return JsonPath.<Number>read(json, path).intValue();
    }
}
