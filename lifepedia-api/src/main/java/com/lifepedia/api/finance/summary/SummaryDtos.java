package com.lifepedia.api.finance.summary;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class SummaryDtos {

    private SummaryDtos() {
    }

    public record Totals(BigDecimal income, BigDecimal expense, BigDecimal balance) {

        public static Totals of(BigDecimal income, BigDecimal expense) {
            return new Totals(income, expense, income.subtract(expense));
        }
    }

    public record CategoryBreakdown(UUID categoryId, String name, String color,
                                    BigDecimal fixed, BigDecimal occasional, BigDecimal total) {
    }

    /** Gastos/ganhos da categoria no mês agrupados pela descrição; fixo = um item. */
    public record CategoryItem(String description, boolean fixed, long count, BigDecimal total) {
    }

    /** @param fixedCommitmentPercent % da renda fixa comprometida com despesas fixas; nulo sem renda fixa */
    public record MonthlySummary(
            YearMonth month,
            Totals fixed,
            Totals occasional,
            Totals total,
            BigDecimal fixedCommitmentPercent,
            List<CategoryBreakdown> expensesByCategory,
            List<CategoryBreakdown> incomesByCategory) {
    }

    public record MonthPoint(
            YearMonth month,
            BigDecimal fixedIncome,
            BigDecimal fixedExpense,
            BigDecimal occasionalIncome,
            BigDecimal occasionalExpense,
            BigDecimal balance) {
    }
}
