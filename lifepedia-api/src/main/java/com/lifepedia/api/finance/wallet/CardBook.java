package com.lifepedia.api.finance.wallet;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.Installments;
import com.lifepedia.api.finance.Installments.Installment;
import com.lifepedia.api.finance.category.Category;
import com.lifepedia.api.finance.recurring.RecurringEntry;
import com.lifepedia.api.finance.transaction.Transaction;
import com.lifepedia.api.finance.wallet.WalletDtos.InvoiceDetail;
import com.lifepedia.api.finance.wallet.WalletDtos.InvoiceItem;
import com.lifepedia.api.finance.wallet.WalletDtos.InvoiceSummary;
import com.lifepedia.api.finance.wallet.WalletDtos.Limit;
import com.lifepedia.api.finance.wallet.WalletDtos.MonthTotal;
import com.lifepedia.api.finance.wallet.WalletDtos.Projection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Faturas de um cartão calculadas a partir dos lançamentos e fixos dele (tudo carregado antes; sem banco). */
final class CardBook {

    private static final int AVERAGE_OF = 3;
    private static final int MIN_DAYS_TO_PROJECT = 3;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Card card;
    private final CardCycle cycle;
    private final LocalDate today;
    private final LocalDate since;
    private final List<Transaction> transactions;
    private final List<RecurringEntry> fixed;
    private final Map<UUID, Map<YearMonth, BigDecimal>> adjustments;
    private final Map<YearMonth, Invoice> invoices;

    /**
     * @param since        dia em que o cartão foi cadastrado: fatura que venceu antes conta como paga
     * @param transactions ocasionais do cartão
     * @param fixed        fixos do cartão
     */
    CardBook(Card card, LocalDate today, LocalDate since, List<Transaction> transactions, List<RecurringEntry> fixed,
             Map<UUID, Map<YearMonth, BigDecimal>> adjustments, Map<YearMonth, Invoice> invoices) {
        this.card = card;
        this.cycle = card.cycle();
        this.today = today;
        this.since = since;
        this.transactions = transactions;
        this.fixed = fixed;
        this.adjustments = adjustments;
        this.invoices = invoices;
    }

    Card card() {
        return card;
    }

    CardCycle cycle() {
        return cycle;
    }

    /** Fatura aberta hoje. */
    YearMonth current() {
        return cycle.invoiceOf(today);
    }

    BigDecimal total(YearMonth month) {
        BigDecimal occasional = transactions.stream().filter(t -> t.getMonth().equals(month))
                .map(t -> signed(t.getType(), t.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal recurring = fixed.stream().filter(e -> e.appliesTo(month))
                .map(e -> signed(e.getType(), amountIn(e, month))).reduce(BigDecimal.ZERO, BigDecimal::add);
        return occasional.add(recurring);
    }

    InvoiceStatus status(YearMonth month) {
        Invoice invoice = invoices.get(month);
        if (invoice != null && invoice.isPaid()) return InvoiceStatus.PAID;
        InvoiceStatus status = cycle.status(month, today);
        boolean closed = status == InvoiceStatus.CLOSED || status == InvoiceStatus.OVERDUE;
        if (closed && (total(month).signum() <= 0 || cycle.dueOn(month).isBefore(since))) return InvoiceStatus.PAID;
        return status;
    }

    InvoiceSummary summary(YearMonth month) {
        Invoice invoice = invoices.get(month);
        boolean paid = invoice != null && invoice.isPaid();
        return new InvoiceSummary(month, status(month), cycle.opensOn(month), cycle.closesOn(month), cycle.dueOn(month),
                total(month), paid ? invoice.getPaidOn() : null, paid ? invoice.getPaidAmount() : null,
                ChronoUnit.DAYS.between(today, cycle.closesOn(month)), ChronoUnit.DAYS.between(today, cycle.dueOn(month)));
    }

    /** Fatura anterior à aberta, se fechada e ainda não paga. */
    Optional<InvoiceSummary> pending() {
        YearMonth previous = current().minusMonths(1);
        InvoiceStatus status = status(previous);
        return status == InvoiceStatus.CLOSED || status == InvoiceStatus.OVERDUE ? Optional.of(summary(previous)) : Optional.empty();
    }

    /** Uso do limite: fatura fechada a pagar, a aberta e as parcelas já lançadas nas próximas. */
    Limit limit() {
        if (card.getCreditLimit() == null) return null;
        YearMonth current = current();
        BigDecimal used = pending().map(InvoiceSummary::total).orElse(BigDecimal.ZERO).add(total(current))
                .add(transactions.stream().filter(t -> t.getMonth().isAfter(current))
                        .map(t -> signed(t.getType(), t.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add))
                .max(BigDecimal.ZERO);
        return new Limit(card.getCreditLimit(), used, card.getCreditLimit().subtract(used));
    }

    InvoiceDetail detail(YearMonth month, Map<UUID, Category> categories) {
        List<InvoiceItem> items = new ArrayList<>();
        LocalDate opens = cycle.opensOn(month);
        BigDecimal purchases = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        int checked = 0;
        int count = 0;
        for (Transaction t : transactions) {
            if (!t.getMonth().equals(month)) continue;
            Optional<Installment> installment = Installments.parse(t.getDescription());
            Category category = categories.get(t.getCategoryId());
            items.add(new InvoiceItem(t.getId(), null, t.getDescription(), t.getDate(), t.getType(), t.getAmount(),
                    category == null ? null : category.getName(), category == null ? null : category.getColor(), t.isChecked(),
                    installment.map(i -> i.number() + "/" + i.total()).orElse(null)));
            count++;
            if (t.isChecked()) checked++;
            if (t.getType() == EntryType.INCOME) credits = credits.add(t.getAmount());
            else if (isPurchase(t, opens, installment)) purchases = purchases.add(t.getAmount());
        }
        for (RecurringEntry e : fixed) {
            if (!e.appliesTo(month)) continue;
            Category category = categories.get(e.getCategoryId());
            LocalDate date = month.atDay(Math.min(e.getDayOfMonth() == null ? 1 : e.getDayOfMonth(), month.lengthOfMonth()));
            items.add(new InvoiceItem(null, e.getId(), e.getDescription(), date, e.getType(), amountIn(e, month),
                    category == null ? null : category.getName(), category == null ? null : category.getColor(), false, null));
            if (e.getType() == EntryType.INCOME) credits = credits.add(amountIn(e, month));
        }
        items.sort(Comparator.comparing(InvoiceItem::date).reversed().thenComparing(InvoiceItem::description));
        BigDecimal total = total(month);
        BigDecimal committed = total.add(credits).subtract(purchases);
        return new InvoiceDetail(summary(month), items, checked, count, purchases, committed, credits,
                projection(month, total, purchases));
    }

    List<MonthTotal> history(YearMonth month, int months) {
        List<MonthTotal> list = new ArrayList<>(months);
        for (YearMonth m = month.minusMonths(months); m.isBefore(month); m = m.plusMonths(1)) {
            list.add(new MonthTotal(m, total(m), status(m)));
        }
        return list;
    }

    List<MonthTotal> upcoming(YearMonth month, int months) {
        List<MonthTotal> list = new ArrayList<>(months);
        for (int i = 1; i <= months; i++) {
            YearMonth m = month.plusMonths(i);
            list.add(new MonthTotal(m, total(m), status(m)));
        }
        return list;
    }

    /** Faturas não pagas que vencem depois de {@code after} e até {@code until}. */
    BigDecimal unpaidDue(LocalDate after, LocalDate until) {
        BigDecimal sum = BigDecimal.ZERO;
        for (YearMonth m = YearMonth.from(after).minusMonths(1); !m.isAfter(YearMonth.from(until).plusMonths(1)); m = m.plusMonths(1)) {
            LocalDate due = cycle.dueOn(m);
            if (!due.isAfter(after) || due.isAfter(until) || status(m) == InvoiceStatus.PAID) continue;
            sum = sum.add(total(m).max(BigDecimal.ZERO));
        }
        return sum;
    }

    /** Pagamentos feitos pela conta depois de {@code after} e até {@code until}. */
    BigDecimal paid(UUID accountId, boolean defaultAccount, LocalDate after, LocalDate until) {
        return invoices.values().stream()
                .filter(i -> i.isPaid() && i.getPaidOn().isAfter(after) && !i.getPaidOn().isAfter(until))
                .filter(i -> accountId.equals(i.getPaidAccount()) || (i.getPaidAccount() == null && defaultAccount))
                .map(Invoice::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Fatura aberta: fixos e parcelas já certos + compras no ritmo do ciclo até o fechamento. */
    private Projection projection(YearMonth month, BigDecimal total, BigDecimal purchases) {
        long cycleDays = ChronoUnit.DAYS.between(cycle.opensOn(month), cycle.closesOn(month));
        long elapsed = Math.clamp(ChronoUnit.DAYS.between(cycle.opensOn(month), today) + 1, 0, cycleDays);
        BigDecimal projected = null;
        if (cycle.status(month, today) == InvoiceStatus.OPEN && elapsed >= MIN_DAYS_TO_PROJECT) {
            projected = total.subtract(purchases).add(purchases.multiply(BigDecimal.valueOf(cycleDays))
                    .divide(BigDecimal.valueOf(elapsed), 2, RoundingMode.HALF_UP));
        }
        List<BigDecimal> previous = new ArrayList<>();
        for (int i = 1; i <= 12 && previous.size() < AVERAGE_OF; i++) {
            BigDecimal value = total(month.minusMonths(i));
            if (value.signum() > 0) previous.add(value);
        }
        BigDecimal average = previous.isEmpty() ? null : previous.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(previous.size()), 2, RoundingMode.HALF_UP);
        Integer budgetPercent = card.getBudget() == null ? null
                : total.max(BigDecimal.ZERO).multiply(HUNDRED).divide(card.getBudget(), 0, RoundingMode.HALF_UP).intValue();
        return new Projection(projected, average, cycleDays, elapsed, budgetPercent);
    }

    private BigDecimal amountIn(RecurringEntry entry, YearMonth month) {
        return adjustments.getOrDefault(entry.getId(), Map.of()).getOrDefault(month, entry.getAmount());
    }

    /** Compra do ciclo (o ritmo da previsão): à vista, feita depois da abertura; parcelas são valor certo. */
    private static boolean isPurchase(Transaction t, LocalDate opens, Optional<Installment> installment) {
        return !t.getDate().isBefore(opens) && installment.isEmpty();
    }

    private static BigDecimal signed(EntryType type, BigDecimal amount) {
        return type == EntryType.INCOME ? amount.negate() : amount;
    }
}
