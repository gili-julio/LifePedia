package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.financing.BcbClient;
import com.lifepedia.api.financing.BcbClient.BankRate;
import com.lifepedia.api.financing.BcbClient.IndexPoint;
import com.lifepedia.api.financing.BcbClient.Published;
import com.lifepedia.api.financing.CorrectionIndex;
import com.lifepedia.api.financing.FinancingService;
import com.lifepedia.api.financing.Modality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FinancingIntegrationTest extends ApiTestSupport {

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private final YearMonth now = YearMonth.from(today);

    @MockitoBean
    BcbClient bcb;

    @Autowired
    FinancingService financings;

    @BeforeEach
    void rates() {
        List<BankRate> banks = IntStream.range(0, 5)
                .mapToObj(i -> new BankRate(i + 1, "Banco " + (i + 1), BigDecimal.valueOf(1.0 + i * 0.5),
                        BigDecimal.valueOf(12.68 + i * 7)))
                .toList();
        when(bcb.rates(any(Modality.class))).thenReturn(Optional.of(new Published(today.minusDays(10), banks)));
        when(bcb.index(any(CorrectionIndex.class), anyInt())).thenReturn(IntStream.range(1, 13)
                .mapToObj(i -> new IndexPoint(now.minusMonths(i), new BigDecimal("0.10"))).toList());
    }

    @Test
    void simulatesWithBankRatesCostsAndIncome() {
        String me = registerAndGetBearer();
        String car = body(send("POST", me, "/api/financings/simulate", """
                {"kind":"CAR","condition":"NEW","price":100000,"downPayment":20000,"months":48,"income":10000}"""));
        assertThat(JsonPath.<List<String>>read(car, "$.scenarios[*].scenario")).containsExactly("LOW", "MEDIAN", "HIGH");
        assertThat(number(car, "$.scenarios[1].monthlyRate")).isEqualTo(2.0);
        assertThat(number(car, "$.scenarios[0].monthlyRate")).isEqualTo(1.5);
        assertThat(JsonPath.<List<String>>read(car, "$.costs[*].key")).contains("iof", "tac", "ipva", "plates");
        assertThat(number(car, "$.financed")).isGreaterThan(80_000d);
        assertThat(JsonPath.<String>read(car, "$.system")).isEqualTo("PRICE");
        assertThat(JsonPath.<List<Object>>read(car, "$.schedule")).hasSize(48);
        assertThat(JsonPath.<List<Object>>read(car, "$.banks")).hasSize(5);
        assertThat(JsonPath.<List<Integer>>read(car, "$.terms[*].months")).containsExactly(12, 24, 36, 48, 60);
        assertThat(number(car, "$.income.percent")).isGreaterThan(20d);
        assertThat(number(car, "$.scenarios[1].price.cetYearly"))
                .isGreaterThan(number(car, "$.scenarios[1].yearlyRate"));

        // Taxa própria entra como cenário e a edição de um gasto muda o valor financiado.
        String custom = body(send("POST", me, "/api/financings/simulate", """
                {"kind":"CAR","condition":"NEW","price":100000,"downPayment":20000,"months":48,"customRate":1.2,
                 "costs":{"tac":0}}"""));
        assertThat(JsonPath.<String>read(custom, "$.scenario")).isEqualTo("CUSTOM");
        assertThat(number(custom, "$.financed")).isLessThan(number(car, "$.financed"));

        String house = body(send("POST", me, "/api/financings/simulate", """
                {"kind":"APARTMENT","condition":"USED","price":400000,"downPayment":40000,"fgts":20000}"""));
        assertThat(JsonPath.<String>read(house, "$.system")).isEqualTo("SAC");
        assertThat(JsonPath.<String>read(house, "$.correction.index")).isEqualTo("TR");
        assertThat(number(house, "$.base")).isEqualTo(340_000d);
        assertThat(JsonPath.<List<String>>read(house, "$.costs[*].key")).contains("itbi", "registry", "mip", "condo");
        assertThat(JsonPath.<List<String>>read(house, "$.warnings")).anyMatch(w -> w.contains("80%"));

        assertThat(send("POST", me, "/api/financings/simulate", """
                {"kind":"CAR","condition":"NEW","price":50000,"downPayment":50000}""")).hasStatus(400);
        assertThat(send("POST", me, "/api/financings/simulate", """
                {"kind":"CAR","condition":"NEW","price":50000,"downPayment":0,"months":100}""")).hasStatus(400);
        assertThat(send("POST", me, "/api/financings/simulate", """
                {"kind":"CAR","condition":"NEW","price":500,"downPayment":0}""")).hasStatus(400);

        // Simulação salva só para o dono.
        String saved = post(me, "/api/financings/simulations", """
                {"name":"Carro novo","input":{"kind":"CAR","condition":"NEW","price":100000,"downPayment":20000}}""");
        String savedId = JsonPath.read(saved, "$.id");
        assertThat(number(saved, "$.input.price")).isEqualTo(100_000d);
        String other = registerAndGetBearer();
        assertThat(JsonPath.<List<Object>>read(body(send("GET", other, "/api/financings/simulations", null)), "$")).isEmpty();
        assertThat(send("DELETE", other, "/api/financings/simulations/" + savedId, null)).hasStatus(404);
        assertThat(send("DELETE", me, "/api/financings/simulations/" + savedId, null)).hasStatus(204);

        String rates = body(send("GET", me, "/api/financings/rates?kind=HOUSE", null));
        assertThat(number(rates, "$.median")).isEqualTo(2.0);
        assertThat(JsonPath.<String>read(rates, "$.index")).isEqualTo("TR");
    }

    @Test
    void tracksProgressExtrasCostsFixedAndReminders() {
        String me = registerAndGetBearer();
        YearMonth first = now.minusMonths(3);
        String created = post(me, "/api/financings", contract("Carro", first, 1.5, null));
        String id = JsonPath.read(created, "$.id");
        int paid = 3 + (today.getDayOfMonth() > 15 ? 1 : 0);
        assertThat(JsonPath.<Integer>read(created, "$.progress.paid")).isEqualTo(paid);
        assertThat(JsonPath.<Integer>read(created, "$.progress.remaining")).isEqualTo(48 - paid);
        assertThat(number(created, "$.progress.balance")).isLessThan(50_000d);
        assertThat(number(created, "$.progress.payoffSavings")).isGreaterThan(0d);
        assertThat(JsonPath.<List<Object>>read(created, "$.members")).hasSize(1);

        // Amortização extra: prévia sem salvar e depois registrada.
        String month = now.plusMonths(1).toString();
        String preview = body(send("POST", me, "/api/financings/" + id + "/extras/preview", """
                {"month":"%s","amount":10000,"mode":"TERM"}""".formatted(month)));
        assertThat(JsonPath.<Integer>read(preview, "$.monthsAfter")).isLessThan(JsonPath.<Integer>read(preview, "$.monthsBefore"));
        assertThat(number(preview, "$.interestSaved")).isGreaterThan(0d);
        String withExtra = body(send("POST", me, "/api/financings/" + id + "/extras", """
                {"month":"%s","amount":10000,"mode":"TERM"}""".formatted(month)));
        assertThat(JsonPath.<Integer>read(withExtra, "$.progress.totalMonths")).isLessThan(48);
        String extraId = JsonPath.read(withExtra, "$.extras[0].id");
        assertThat(send("POST", me, "/api/financings/" + id + "/extras", """
                {"month":"%s","amount":100,"mode":"TERM"}""".formatted(first.minusMonths(1)))).hasStatus(400);

        // Fixo pessoal com a parcela, atualizado quando o contrato muda.
        String category = categoryId(me, "EXPENSE", "Dívidas e parcelas");
        String withFixed = body(send("PUT", me, "/api/financings/" + id + "/my-fixed", """
                {"enabled":true,"categoryId":"%s"}""".formatted(category)));
        String fixedId = JsonPath.read(withFixed, "$.members[0].recurringEntryId");
        String fixos = body(send("GET", me, "/api/finance/recurring/all", null));
        assertThat(JsonPath.<List<String>>read(fixos, "$[?(@.id == '" + fixedId + "')].startMonth"))
                .containsExactly(first.toString());
        assertThat(send("DELETE", me, "/api/financings/" + id + "/extras/" + extraId, null)).hasStatusOk();
        assertThat(send("PUT", me, "/api/financings/" + id, contract("Carro", first, 2.0, null))).hasStatusOk();
        fixos = body(send("GET", me, "/api/finance/recurring/all", null));
        assertThat(JsonPath.<List<String>>read(fixos, "$[?(@.id == '" + fixedId + "')].endMonth"))
                .containsExactly(first.plusMonths(47).toString());
        assertThat(numbers(fixos, "$[?(@.id == '" + fixedId + "')].amount").getFirst())
                .isGreaterThan(1_600d);

        // Gastos do bem.
        assertThat(send("POST", me, "/api/financings/" + id + "/costs", """
                {"name":"IPVA","amount":3000,"frequency":"YEARLY","dueDay":10}""")).hasStatus(400);
        String withCost = body(send("POST", me, "/api/financings/" + id + "/costs", """
                {"name":"IPVA","amount":3000,"frequency":"YEARLY","dueMonth":3,"dueDay":10}"""));
        assertThat(JsonPath.<String>read(withCost, "$.costs[0].nextDate")).endsWith("-03-10");

        // Aviso 3 dias antes do vencimento da próxima parcela.
        String next = JsonPath.read(withCost, "$.progress.next.date");
        assertThat(send("PUT", me, "/api/financings/" + id, contract("Carro", first, 2.0, 3))).hasStatusOk();
        assertThat(financings.sendReminders(LocalDate.parse(next).minusDays(3))).isPositive();
        String inbox = body(send("GET", me, "/api/notifications?size=10", null));
        assertThat(JsonPath.<List<String>>read(inbox, "$.items[*].type")).contains("FINANCING_DUE");

        // Isolamento, lista geral e arquivo da conta.
        String other = registerAndGetBearer();
        assertThat(send("GET", other, "/api/financings/" + id, null)).hasStatus(404);
        assertThat(JsonPath.<List<String>>read(body(send("GET", me, "/api/financings/overview", null)), "$[*].name"))
                .containsExactly("Carro");
        String exported = body(send("GET", me, "/api/account/export", null));
        assertThat(JsonPath.<List<String>>read(exported, "$.financings[*].name")).containsExactly("Carro");

        // Excluir leva o fixo gerado junto.
        assertThat(send("DELETE", me, "/api/financings/" + id, null)).hasStatus(204);
        fixos = body(send("GET", me, "/api/finance/recurring/all", null));
        assertThat(JsonPath.<List<String>>read(fixos, "$[?(@.id == '" + fixedId + "')].id")).isEmpty();
    }

    @Test
    void groupFinancingSplitsBetweenMembers() {
        String alice = registerAndGetBearer("Alice", newUsername());
        String bob = registerAndGetBearer("Bob", newUsername());
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", bob, "/api/groups/join/" + token, null)).hasStatus2xxSuccessful();
        String base = "/api/groups/" + groupId + "/financings";

        String created = post(alice, base, contract("Apartamento", now, 1.0, null));
        String id = JsonPath.read(created, "$.id");
        assertThat(numbers(created, "$.members[*].share")).containsExactly(50d, 50d);
        assertThat(JsonPath.<List<String>>read(body(send("GET", bob, base, null)), "$[*].name"))
                .containsExactly("Apartamento");
        assertThat(JsonPath.<List<String>>read(body(send("GET", bob, "/api/financings/overview", null)), "$[*].groupName"))
                .containsExactly("Casa");
        assertThat(send("DELETE", bob, base + "/" + id, null)).hasStatus(403);

        String aliceId = userId(alice);
        String bobId = userId(bob);
        assertThat(send("PUT", alice, base + "/" + id, contract("Apartamento", now, 1.0, null)
                .replace("\"shares\":null", shares(aliceId, 70, bobId, 20)))).hasStatus(400);
        String split = body(send("PUT", alice, base + "/" + id, contract("Apartamento", now, 1.0, null)
                .replace("\"shares\":null", shares(aliceId, 60, bobId, 40))));
        assertThat(numbers(split, "$.members[*].share")).containsExactlyInAnyOrder(60d, 40d);

        // Bob leva a parte dele para as finanças pessoais e sincroniza no grupo.
        String category = categoryId(bob, "EXPENSE", "Moradia");
        String mine = body(send("PUT", bob, base + "/" + id + "/my-fixed", """
                {"enabled":true,"categoryId":"%s","shareToGroup":true}""".formatted(category)));
        assertThat(JsonPath.<List<Object>>read(mine, "$.members[?(@.me == true && @.fixed == true)]")).hasSize(1);
        String groupFixed = body(send("GET", alice, "/api/groups/" + groupId + "/finance/recurring", null));
        assertThat(JsonPath.<List<String>>read(groupFixed, "$.items[*].description")).containsExactly("Apartamento");

        assertThat(send("POST", bob, "/api/groups/" + groupId + "/leave", null)).hasStatus2xxSuccessful();
        assertThat(JsonPath.<List<Object>>read(body(send("GET", alice, base + "/" + id, null)), "$.members")).hasSize(1);
        assertThat(send("GET", bob, base + "/" + id, null)).hasStatus(404);
    }

    private static double number(String json, String path) {
        return JsonPath.<Number>read(json, path).doubleValue();
    }

    private static List<Double> numbers(String json, String path) {
        return JsonPath.<List<Number>>read(json, path).stream().map(Number::doubleValue).toList();
    }

    private static String contract(String name, YearMonth first, double rate, Integer reminder) {
        return """
                {"name":"%s","kind":"CAR","condition":"NEW","price":60000,"downPayment":10000,"financed":50000,
                 "monthlyRate":%s,"system":"PRICE","months":48,"firstMonth":"%s","dueDay":15,"index":"NONE",
                 "reminderDays":%s,"shares":null}""".formatted(name, rate, first, reminder);
    }

    private static String shares(String a, int shareA, String b, int shareB) {
        return """
                "shares":[{"userId":"%s","share":%d},{"userId":"%s","share":%d}]""".formatted(a, shareA, b, shareB);
    }
}
