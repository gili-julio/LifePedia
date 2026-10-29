package com.lifepedia.api.finance.summary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class SummaryDtos {

    private SummaryDtos() {
    }

    /**
     * @param expense  despesas sem os investimentos
     * @param invested despesas em categoria de investimento ({@code InvestmentCategories})
     * @param balance  ganhos - despesas - investido
     */
    public record Totals(BigDecimal income, BigDecimal expense, BigDecimal invested, BigDecimal balance) {

        public static Totals of(BigDecimal income, BigDecimal expense, BigDecimal invested) {
            return new Totals(income, expense, invested, income.subtract(expense).subtract(invested));
        }

        public Totals plus(Totals other) {
            return of(income.add(other.income), expense.add(other.expense), invested.add(other.invested));
        }
    }

    public record CategoryBreakdown(UUID categoryId, String name, String color,
                                    BigDecimal fixed, BigDecimal occasional, BigDecimal total) {
    }

    /** Gastos/ganhos da categoria no mês agrupados pela descrição; fixo = um item. */
    public record CategoryItem(String description, boolean fixed, long count, BigDecimal total) {
    }

    /**
     * @param fixedCommitmentPercent % da renda fixa comprometida com despesas fixas; nulo sem renda fixa
     * @param investedPercent        % dos ganhos do mês que foi investido; nulo sem ganhos
     */
    public record MonthlySummary(
            YearMonth month,
            Totals fixed,
            Totals occasional,
            Totals total,
            BigDecimal fixedCommitmentPercent,
            BigDecimal investedPercent,
            List<CategoryBreakdown> expensesByCategory,
            List<CategoryBreakdown> incomesByCategory) {
    }

    /** Despesas sem os investimentos; {@code invested} (fixos + ocasionais) também desconta do saldo. */
    public record MonthPoint(
            YearMonth month,
            BigDecimal fixedIncome,
            BigDecimal fixedExpense,
            BigDecimal occasionalIncome,
            BigDecimal occasionalExpense,
            BigDecimal invested,
            BigDecimal balance) {
    }

    /** % de {@code part} sobre {@code whole} (1 casa); nulo se {@code whole} for zero. */
    public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? null : part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }
}
