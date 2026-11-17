package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.finance.wallet.CardCycle;
import com.lifepedia.api.finance.wallet.WalletReminderJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class WalletIntegrationTest extends ApiTestSupport {

    @Autowired
    WalletReminderJob reminders;

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private final CardCycle cycle = new CardCycle(3, 10);

    @Test
    void cardInvoicesPurchasesRecalculationPaymentAndAccounts() {
        String me = registerAndGetBearer();
        String transport = categoryId(me, "EXPENSE", "Transporte");
        YearMonth open = cycle.invoiceOf(today);

        String cardId = JsonPath.read(post(me, "/api/finance/wallet/cards", """
                {"bank":{"code":"NUBANK"},"nickname":"Roxinho","lastDigits":"1234","closingDay":3,"dueDay":10,
                 "creditLimit":3000,"budget":50}"""), "$.card.id");
        assertThat(send("POST", me, "/api/finance/wallet/cards", """
                {"bank":{"code":"NUBANK"},"closingDay":5,"dueDay":12}""")).hasStatus(409);
        assertThat(send("POST", me, "/api/finance/wallet/cards", """
                {"bank":{"code":"INTER"},"closingDay":0,"dueDay":12}""")).hasStatus(400);
        String card = "/api/finance/wallet/cards/" + cardId;

        // Sem mês, a compra no cartão cai na fatura do ciclo; com mês, vale o informado.
        String bread = JsonPath.read(post(me, "/api/finance/transactions", """
                {"description":"Padaria","type":"EXPENSE","categoryId":"%s","amount":50,"date":"%s",
                 "bank":{"code":"NUBANK","kind":"CARD"}}""".formatted(transport, today)), "$.id");
        assertThat(send("GET", me, "/api/finance/transactions?month=" + open, null)).bodyJson()
                .extractingPath("$.items[0].id").isEqualTo(bread);
        String wrong = JsonPath.read(post(me, "/api/finance/transactions", """
                {"description":"Cinema","type":"EXPENSE","categoryId":"%s","amount":30,"date":"%s","month":"%s",
                 "bank":{"code":"NUBANK","kind":"CARD"}}""".formatted(transport, today, open.plusMonths(2))), "$.id");

        // Lançamento rápido: categoria do último com o mesmo nome; parcelas nas faturas seguintes.
        assertThat(send("POST", me, "/api/finance/wallet/purchases", """
                {"cardId":"%s","description":"Padaria","amount":20}""".formatted(cardId))).bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.categoryId").isEqualTo(transport);
                    assertThat(json).extractingPath("$.firstMonth").isEqualTo(open.toString());
                });
        post(me, "/api/finance/wallet/purchases", """
                {"cardId":"%s","description":"TV","amount":100,"installments":3}""".formatted(cardId));
        assertThat(send("GET", me, card, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.card.label").isEqualTo("Roxinho");
            assertThat(json).extractingPath("$.invoice.summary.month").isEqualTo(open.toString());
            assertThat(json).extractingPath("$.invoice.summary.status").isEqualTo("OPEN");
            assertThat(json).extractingPath("$.invoice.summary.total").isEqualTo(103.34);
            assertThat(json).extractingPath("$.invoice.items.length()").isEqualTo(3);
            assertThat(json).extractingPath("$.upcoming[0].total").isEqualTo(33.33);
            assertThat(json).extractingPath("$.upcoming[1].total").isEqualTo(63.33);
            assertThat(json).extractingPath("$.card.limit.used").isEqualTo(200.0);
        });
        assertThat(send("POST", me, "/api/finance/wallet/purchases", """
                {"description":"Sem destino","amount":20}""")).hasStatus(400);

        // Recalcular: só o lançado à mão fora do ciclo, e só se escolhido.
        assertThat(send("GET", me, card + "/recalculate?from=" + YearMonth.from(today).minusMonths(1), null)).bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.length()").isEqualTo(1);
                    assertThat(json).extractingPath("$[0].id").isEqualTo(wrong);
                    assertThat(json).extractingPath("$[0].expected").isEqualTo(open.toString());
                });
        assertThat(send("POST", me, card + "/recalculate", """
                {"from":"%s","ids":["%s"]}""".formatted(YearMonth.from(today).minusMonths(1), wrong)))
                .bodyJson().extractingPath("$.moved").isEqualTo(1);
        assertThat(send("GET", me, card, null)).bodyJson().extractingPath("$.invoice.summary.total").isEqualTo(133.34);

        // Pagar e desfazer.
        assertThat(send("PUT", me, card + "/invoices/" + open + "/paid", "{}")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.invoice.summary.status").isEqualTo("PAID");
            assertThat(json).extractingPath("$.invoice.summary.paidAmount").isEqualTo(133.34);
        });
        assertThat(send("DELETE", me, card + "/invoices/" + open + "/paid", null)).bodyJson()
                .extractingPath("$.invoice.summary.status").isEqualTo("OPEN");

        // Conferir com o documento: valor do documento vale.
        assertThat(send("POST", me, "/api/finance/import", """
                {"checks":[{"transactionId":"%s","amount":55}]}""".formatted(bread))).bodyJson()
                .extractingPath("$.checked").isEqualTo(1);
        assertThat(send("GET", me, card, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.invoice.checked").isEqualTo(1);
            assertThat(json).extractingPath("$.invoice.summary.total").isEqualTo(138.34);
        });

        // Conta: saldo informado ontem menos o gasto de hoje.
        String accountId = JsonPath.read(post(me, "/api/finance/wallet/accounts", """
                {"bank":{"code":"INTER"},"balance":1000,"balanceOn":"%s"}""".formatted(today.minusDays(1))), "$.id");
        post(me, "/api/finance/transactions", """
                {"description":"Luz","type":"EXPENSE","categoryId":"%s","amount":100,"date":"%s",
                 "bank":{"code":"INTER","kind":"ACCOUNT"}}""".formatted(transport, today));
        assertThat(send("GET", me, "/api/finance/wallet", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.cards.length()").isEqualTo(1);
            assertThat(json).extractingPath("$.accounts[0].today").isEqualTo(900.0);
            assertThat(json).extractingPath("$.openTotal").isEqualTo(138.34);
        });
        assertThat(send("POST", me, "/api/finance/wallet/purchases", """
                {"accountId":"%s","description":"Pix","amount":20,"installments":2}""".formatted(accountId))).hasStatus(400);
        assertThat(send("PUT", me, "/api/finance/wallet/accounts/" + accountId + "/balance", """
                {"balance":500,"balanceOn":"%s"}""".formatted(today.plusDays(1)))).hasStatus(400);

        // Meta de 50 passada: aviso uma vez só.
        reminders.remind();
        reminders.remind();
        assertThat(reminderKinds(me)).containsOnlyOnce("BUDGET");

        // Isolamento.
        String other = registerAndGetBearer();
        assertThat(send("GET", other, card, null)).hasStatus(404);
        assertThat(send("POST", other, "/api/finance/wallet/purchases", """
                {"cardId":"%s","description":"X","amount":1}""".formatted(cardId))).hasStatus(404);
        assertThat(send("DELETE", other, "/api/finance/wallet/accounts/" + accountId, null)).hasStatus(404);
        assertThat(mvc.get().uri("/api/finance/wallet").exchange()).hasStatus(401);

        assertThat(send("DELETE", me, card, null)).hasStatus(204);
        assertThat(send("GET", me, "/api/finance/transactions?month=" + open, null)).bodyJson()
                .extractingPath("$.totalItems").isEqualTo(4);
    }

    @Test
    void cardsShownInTheGroup() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", bob, "/api/groups/join/" + token, null)).hasStatusOk();

        assertThat(send("POST", bob, "/api/finance/wallet/cards", """
                {"bank":{"code":"ITAU"},"closingDay":3,"dueDay":10,"groupIds":["%s"]}""".formatted(
                JsonPath.<String>read(post(alice, "/api/groups", """
                        {"name":"Outro"}"""), "$.id")))).hasStatus(400);
        String cardId = JsonPath.read(post(alice, "/api/finance/wallet/cards", """
                {"bank":{"code":"ITAU"},"closingDay":3,"dueDay":10,"groupIds":["%s"]}""".formatted(groupId)), "$.card.id");
        String food = categoryId(alice, "EXPENSE", "Alimentação");
        post(alice, "/api/finance/transactions", """
                {"description":"Mercado","type":"EXPENSE","categoryId":"%s","amount":80,"date":"%s",
                 "bank":{"code":"ITAU","kind":"CARD"},"groupIds":["%s"]}""".formatted(food, today, groupId));

        assertThat(send("GET", bob, "/api/groups/" + groupId + "/finance/cards", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.length()").isEqualTo(1);
            assertThat(json).extractingPath("$[0].id").isEqualTo(cardId);
            assertThat(json).extractingPath("$[0].mine").isEqualTo(false);
            assertThat(json).extractingPath("$[0].current.total").isEqualTo(80.0);
            assertThat(json).extractingPath("$[0].groupSpent").isEqualTo(80.0);
        });
        assertThat(send("GET", registerAndGetBearer(), "/api/groups/" + groupId + "/finance/cards", null)).hasStatus(404);

        // Quem sai do grupo deixa de mostrar o cartão ali.
        assertThat(send("POST", alice, "/api/groups/" + groupId + "/leave", null).getResponse().getStatus()).isLessThan(300);
        assertThat(send("GET", alice, "/api/finance/wallet/cards/" + cardId, null)).bodyJson()
                .extractingPath("$.card.groups.length()").isEqualTo(0);
    }

    private List<String> reminderKinds(String bearer) {
        String json = body(send("GET", bearer, "/api/notifications?size=50", null));
        return JsonPath.read(json, "$.items[?(@.type == 'CARD_REMINDER')].data.kind");
    }
}
