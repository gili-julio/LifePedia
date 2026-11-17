package com.lifepedia.api.finance.wallet;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.bank.BankKind;
import com.lifepedia.api.finance.bank.BankRef;
import com.lifepedia.api.finance.recurring.RecurringEntry;
import com.lifepedia.api.finance.transaction.Transaction;
import com.lifepedia.api.finance.wallet.WalletDtos.InvoiceDetail;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CardBookTest {

    private static final UUID USER = UUID.randomUUID();
    private static final BankRef NUBANK = new BankRef("NUBANK", null, BankKind.CARD);

    @Test
    void purchaseOnOrAfterClosingGoesToNextInvoice() {
        CardCycle cycle = new CardCycle(3, 10);
        assertThat(cycle.closesOn(YearMonth.of(2026, 12))).isEqualTo(LocalDate.of(2026, 12, 3));
        assertThat(cycle.opensOn(YearMonth.of(2026, 12))).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(cycle.invoiceOf(LocalDate.of(2026, 11, 20))).isEqualTo(YearMonth.of(2026, 12));
        assertThat(cycle.invoiceOf(LocalDate.of(2026, 12, 2))).isEqualTo(YearMonth.of(2026, 12));
        assertThat(cycle.invoiceOf(LocalDate.of(2026, 12, 3))).isEqualTo(YearMonth.of(2027, 1));
        assertThat(cycle.invoiceOf(LocalDate.of(2026, 11, 20), 3)).isEqualTo(YearMonth.of(2027, 2));
    }

    @Test
    void closingInThePreviousMonthWhenItComesAfterTheDueDay() {
        CardCycle cycle = new CardCycle(28, 5);
        assertThat(cycle.closesOn(YearMonth.of(2026, 12))).isEqualTo(LocalDate.of(2026, 11, 28));
        assertThat(cycle.invoiceOf(LocalDate.of(2026, 11, 27))).isEqualTo(YearMonth.of(2026, 12));
        assertThat(cycle.invoiceOf(LocalDate.of(2026, 11, 28))).isEqualTo(YearMonth.of(2027, 1));
        // Dia que o mês não tem vira o último.
        CardCycle late = new CardCycle(31, 10);
        assertThat(late.closesOn(YearMonth.of(2026, 3))).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void statusFollowsTheCycle() {
        CardCycle cycle = new CardCycle(3, 10);
        YearMonth december = YearMonth.of(2026, 12);
        assertThat(cycle.status(december, LocalDate.of(2026, 11, 2))).isEqualTo(InvoiceStatus.FUTURE);
        assertThat(cycle.status(december, LocalDate.of(2026, 11, 20))).isEqualTo(InvoiceStatus.OPEN);
        assertThat(cycle.status(december, LocalDate.of(2026, 12, 5))).isEqualTo(InvoiceStatus.CLOSED);
        assertThat(cycle.status(december, LocalDate.of(2026, 12, 11))).isEqualTo(InvoiceStatus.OVERDUE);
    }

    @Test
    void totalsPendingLimitAndProjection() {
        Card card = card();
        LocalDate today = LocalDate.of(2026, 11, 12);
        RecurringEntry streaming = fixed("Streaming", "40.00", YearMonth.of(2026, 1));
        List<Transaction> items = List.of(
                tx("Mercado", "300.00", EntryType.EXPENSE, LocalDate.of(2026, 11, 5), YearMonth.of(2026, 12)),
                tx("Estorno", "50.00", EntryType.INCOME, LocalDate.of(2026, 11, 6), YearMonth.of(2026, 12)),
                tx("TV (2/3)", "100.00", EntryType.EXPENSE, LocalDate.of(2026, 10, 1), YearMonth.of(2026, 12)),
                tx("TV (3/3)", "100.00", EntryType.EXPENSE, LocalDate.of(2026, 10, 1), YearMonth.of(2027, 1)),
                tx("Farmácia", "200.00", EntryType.EXPENSE, LocalDate.of(2026, 10, 20), YearMonth.of(2026, 11)));
        // Fixo sem id (não salvo): o mapa de ajustes precisa aceitar chave nula.
        CardBook book = new CardBook(card, today, LocalDate.of(2026, 1, 1), items, List.of(streaming), new HashMap<>(), Map.of());

        assertThat(book.current()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(book.total(YearMonth.of(2026, 12))).isEqualByComparingTo("390.00");
        // A de novembro fechou dia 3, vence dia 10 e não foi paga.
        assertThat(book.pending()).hasValueSatisfying(p -> {
            assertThat(p.status()).isEqualTo(InvoiceStatus.OVERDUE);
            assertThat(p.total()).isEqualByComparingTo("240.00");
        });
        assertThat(book.limit().used()).isEqualByComparingTo("730.00");
        assertThat(book.limit().available()).isEqualByComparingTo("1270.00");

        InvoiceDetail detail = book.detail(YearMonth.of(2026, 12), Map.of());
        assertThat(detail.purchases()).isEqualByComparingTo("300.00");
        assertThat(detail.credits()).isEqualByComparingTo("50.00");
        assertThat(detail.committed()).isEqualByComparingTo("140.00");
        assertThat(detail.items()).hasSize(4);
        // Ciclo de 30 dias, 10 passados: 300 em compras viram 900.
        assertThat(detail.projection().elapsedDays()).isEqualTo(10);
        assertThat(detail.projection().projected()).isEqualByComparingTo("990.00");
        assertThat(detail.projection().budgetPercent()).isEqualTo(78);
    }

    @Test
    void invoicesDueBeforeTheCardWasRegisteredCountAsPaid() {
        LocalDate today = LocalDate.of(2026, 11, 12);
        List<Transaction> items = List.of(tx("Farmácia", "200.00", EntryType.EXPENSE, LocalDate.of(2026, 10, 20), YearMonth.of(2026, 11)));
        CardBook book = new CardBook(card(), today, today, items, List.of(), Map.of(), Map.of());
        assertThat(book.status(YearMonth.of(2026, 11))).isEqualTo(InvoiceStatus.PAID);
        assertThat(book.pending()).isEmpty();
    }

    private static Card card() {
        Card card = new Card(USER);
        card.setBank(NUBANK);
        card.setClosingDay(3);
        card.setDueDay(10);
        card.setCreditLimit(new BigDecimal("2000.00"));
        card.setBudget(new BigDecimal("500.00"));
        return card;
    }

    private static Transaction tx(String description, String amount, EntryType type, LocalDate date, YearMonth month) {
        Transaction t = new Transaction(USER);
        t.setDescription(description);
        t.setAmount(new BigDecimal(amount));
        t.setType(type);
        t.setDate(date);
        t.setMonth(month);
        t.setBank(NUBANK);
        t.setCategoryId(UUID.randomUUID());
        return t;
    }

    private static RecurringEntry fixed(String description, String amount, YearMonth start) {
        RecurringEntry e = new RecurringEntry(USER);
        e.setDescription(description);
        e.setAmount(new BigDecimal(amount));
        e.setType(EntryType.EXPENSE);
        e.setStartMonth(start);
        e.setActive(true);
        e.setDayOfMonth(15);
        e.setBank(NUBANK);
        e.setCategoryId(UUID.randomUUID());
        return e;
    }
}
