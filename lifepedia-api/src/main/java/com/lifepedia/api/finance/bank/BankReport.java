package com.lifepedia.api.finance.bank;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.bank.BankDtos.BankCategory;
import com.lifepedia.api.finance.bank.BankDtos.BankLine;
import com.lifepedia.api.finance.bank.BankDtos.BankOverview;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Soma os itens (fixos já com o valor de cada mês) por banco; usado no resumo pessoal e no do grupo. */
public final class BankReport {

    private static final String NO_BANK = "Sem banco";

    /** Valor de um item num mês. */
    public record Item(BankRef bank, YearMonth month, EntryType type, boolean investment, boolean fixed,
                       String category, String color, BigDecimal amount) {
    }

    private BankReport() {
    }

    /** @param months meses de {@code month} para trás no histórico */
    public static BankOverview build(YearMonth month, int months, List<Item> items) {
        List<YearMonth> series = new ArrayList<>(months);
        for (YearMonth m = month.minusMonths(months - 1L); !m.isAfter(month); m = m.plusMonths(1)) series.add(m);

        Map<String, Line> lines = new LinkedHashMap<>();
        for (Item item : items) {
            String key = item.bank() == null ? BankFilter.NONE : item.bank().key();
            lines.computeIfAbsent(key, k -> new Line(k, item.bank(), series)).add(item, month);
        }
        List<BankLine> banks = lines.values().stream()
                .sorted(Comparator.<Line, Boolean>comparing(l -> l.bank == null)
                        .thenComparing(l -> l.expense, Comparator.reverseOrder())
                        .thenComparing(l -> l.name()))
                .map(Line::toResponse)
                .toList();
        return new BankOverview(month, series, banks.stream().map(BankLine::expense).reduce(BigDecimal.ZERO, BigDecimal::add), banks);
    }

    private static final class Line {
        final String key;
        final BankRef bank;
        final List<YearMonth> series;
        final Map<YearMonth, BigDecimal> history = new LinkedHashMap<>();
        final Map<String, Slice> categories = new LinkedHashMap<>();
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal fixed = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        BigDecimal account = BigDecimal.ZERO;

        Line(String key, BankRef bank, List<YearMonth> series) {
            this.key = key;
            this.bank = bank;
            this.series = series;
        }

        void add(Item item, YearMonth month) {
            boolean expenseItem = item.type() == EntryType.EXPENSE && !item.investment();
            if (expenseItem) history.merge(item.month(), item.amount(), BigDecimal::add);
            if (!item.month().equals(month)) return;
            if (item.type() == EntryType.INCOME) income = income.add(item.amount());
            else if (item.investment()) invested = invested.add(item.amount());
            if (!expenseItem) return;
            expense = expense.add(item.amount());
            if (item.fixed()) fixed = fixed.add(item.amount());
            if (item.bank() != null && item.bank().kind() == BankKind.CARD) card = card.add(item.amount());
            else if (item.bank() != null) account = account.add(item.amount());
            categories.computeIfAbsent(item.category().trim().toLowerCase(Locale.ROOT), k -> new Slice(item)).add(item.amount());
        }

        String name() {
            return bank == null ? NO_BANK : bank.label();
        }

        BankLine toResponse() {
            return new BankLine(key, bank == null ? null : bank.code(), name(), income, expense, invested, fixed,
                    expense.subtract(fixed), account, card,
                    categories.values().stream()
                            .sorted(Comparator.comparing((Slice s) -> s.total).reversed())
                            .map(s -> new BankCategory(s.name, s.color, s.total))
                            .toList(),
                    series.stream().map(m -> history.getOrDefault(m, BigDecimal.ZERO)).toList());
        }
    }

    /** Categoria somada pelo nome; nome e cor da primeira encontrada. */
    private static final class Slice {
        final String name;
        final String color;
        BigDecimal total = BigDecimal.ZERO;

        Slice(Item item) {
            this.name = item.category();
            this.color = item.color();
        }

        void add(BigDecimal amount) {
            total = total.add(amount);
        }
    }
}
