package com.lifepedia.api.finance.summary;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.category.Category;
import com.lifepedia.api.finance.category.CategoryService;
import com.lifepedia.api.finance.recurring.RecurringAdjustment;
import com.lifepedia.api.finance.recurring.RecurringAdjustmentRepository;
import com.lifepedia.api.finance.recurring.RecurringEntry;
import com.lifepedia.api.finance.recurring.RecurringEntryRepository;
import com.lifepedia.api.finance.summary.SummaryDtos.CategoryBreakdown;
import com.lifepedia.api.finance.summary.SummaryDtos.CategoryItem;
import com.lifepedia.api.finance.summary.SummaryDtos.MonthPoint;
import com.lifepedia.api.finance.summary.SummaryDtos.MonthlySummary;
import com.lifepedia.api.finance.summary.SummaryDtos.Totals;
import com.lifepedia.api.finance.transaction.Transaction;
import com.lifepedia.api.finance.transaction.TransactionRepository;
import com.lifepedia.api.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Comparativo de ganhos x despesas, fixos x ocasionais. */
@Service
public class SummaryService {

    private final RecurringEntryRepository recurringRepository;
    private final RecurringAdjustmentRepository adjustmentRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;

    public SummaryService(RecurringEntryRepository recurringRepository, RecurringAdjustmentRepository adjustmentRepository,
                          TransactionRepository transactionRepository, CategoryService categoryService) {
        this.recurringRepository = recurringRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.transactionRepository = transactionRepository;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public MonthlySummary monthly(UUID userId, YearMonth month) {
        List<RecurringEntry> fixed = recurringRepository.findByUserIdOrderByTypeAscAmountDesc(userId).stream()
                .filter(e -> e.appliesTo(month))
                .toList();
        List<Transaction> occasional = transactionRepository.findByUserIdAndMonthOrderByDateDescCreatedAtDesc(userId, month);
        Function<RecurringEntry, BigDecimal> fixedAmount = amountIn(month, adjustments(userId));

        Totals fixedTotals = Totals.of(
                sum(fixed.stream(), EntryType.INCOME, RecurringEntry::getType, fixedAmount),
                sum(fixed.stream(), EntryType.EXPENSE, RecurringEntry::getType, fixedAmount));
        Totals occasionalTotals = Totals.of(
                sum(occasional.stream(), EntryType.INCOME, Transaction::getType, Transaction::getAmount),
                sum(occasional.stream(), EntryType.EXPENSE, Transaction::getType, Transaction::getAmount));
        Totals total = Totals.of(
                fixedTotals.income().add(occasionalTotals.income()),
                fixedTotals.expense().add(occasionalTotals.expense()));

        BigDecimal commitment = fixedTotals.income().signum() == 0 ? null
                : fixedTotals.expense().multiply(BigDecimal.valueOf(100))
                .divide(fixedTotals.income(), 1, RoundingMode.HALF_UP);

        Map<UUID, Category> categories = categoryService.mapByIdFor(userId);
        return new MonthlySummary(month, fixedTotals, occasionalTotals, total, commitment,
                breakdown(fixed, fixedAmount, occasional, EntryType.EXPENSE, categories),
                breakdown(fixed, fixedAmount, occasional, EntryType.INCOME, categories));
    }

    @Transactional(readOnly = true)
    public List<CategoryItem> categoryItems(UUID userId, UUID categoryId, YearMonth month) {
        if (!categoryService.mapByIdFor(userId).containsKey(categoryId)) {
            throw new NotFoundException("Categoria não encontrada.");
        }
        Function<RecurringEntry, BigDecimal> fixedAmount = amountIn(month, adjustments(userId));
        Stream<CategoryItem> fixed = recurringRepository.findByUserIdOrderByTypeAscAmountDesc(userId).stream()
                .filter(e -> e.appliesTo(month) && e.getCategoryId().equals(categoryId))
                .map(e -> new CategoryItem(e.getDescription(), true, 1, fixedAmount.apply(e)));
        Stream<CategoryItem> occasional = transactionRepository.groupByDescription(userId, month, categoryId).stream()
                .map(g -> new CategoryItem(g.getDescription(), false, g.getCount(), g.getTotal()));
        return Stream.concat(fixed, occasional)
                .sorted(Comparator.comparing(CategoryItem::total).reversed())
                .toList();
    }

    /** Série de {@code months} meses até {@code until}, do mais antigo ao mais novo. */
    @Transactional(readOnly = true)
    public List<MonthPoint> history(UUID userId, YearMonth until, int months) {
        YearMonth from = until.minusMonths(months - 1L);
        List<RecurringEntry> fixed = recurringRepository.findByUserIdOrderByTypeAscAmountDesc(userId);
        Map<UUID, Map<YearMonth, BigDecimal>> adjustments = adjustments(userId);
        List<Transaction> occasional = transactionRepository.findByUserIdAndMonthBetween(userId, from, until);

        List<MonthPoint> points = new ArrayList<>(months);
        for (YearMonth month = from; !month.isAfter(until); month = month.plusMonths(1)) {
            YearMonth current = month;
            List<RecurringEntry> monthFixed = fixed.stream().filter(e -> e.appliesTo(current)).toList();
            List<Transaction> monthOccasional = occasional.stream()
                    .filter(t -> t.getMonth().equals(current)).toList();

            Function<RecurringEntry, BigDecimal> fixedAmount = amountIn(current, adjustments);
            BigDecimal fixedIncome = sum(monthFixed.stream(), EntryType.INCOME, RecurringEntry::getType, fixedAmount);
            BigDecimal fixedExpense = sum(monthFixed.stream(), EntryType.EXPENSE, RecurringEntry::getType, fixedAmount);
            BigDecimal occIncome = sum(monthOccasional.stream(), EntryType.INCOME, Transaction::getType, Transaction::getAmount);
            BigDecimal occExpense = sum(monthOccasional.stream(), EntryType.EXPENSE, Transaction::getType, Transaction::getAmount);
            BigDecimal balance = fixedIncome.add(occIncome).subtract(fixedExpense).subtract(occExpense);
            points.add(new MonthPoint(current, fixedIncome, fixedExpense, occIncome, occExpense, balance));
        }
        return points;
    }

    /** Valor ajustado por fixo e mês. */
    private Map<UUID, Map<YearMonth, BigDecimal>> adjustments(UUID userId) {
        return adjustmentRepository.findByUserId(userId).stream().collect(Collectors.groupingBy(
                RecurringAdjustment::getRecurringEntryId,
                Collectors.toMap(RecurringAdjustment::getMonth, RecurringAdjustment::getAmount)));
    }

    /** Valor do fixo no mês: o ajustado, se houver; senão o cadastrado. */
    private static Function<RecurringEntry, BigDecimal> amountIn(YearMonth month, Map<UUID, Map<YearMonth, BigDecimal>> adjustments) {
        return e -> adjustments.getOrDefault(e.getId(), Map.of()).getOrDefault(month, e.getAmount());
    }

    private static List<CategoryBreakdown> breakdown(List<RecurringEntry> fixed, Function<RecurringEntry, BigDecimal> fixedAmount,
                                                     List<Transaction> occasional, EntryType type,
                                                     Map<UUID, Category> categories) {
        Map<UUID, BigDecimal> fixedByCategory = sumByCategory(fixed.stream(), type,
                RecurringEntry::getType, RecurringEntry::getCategoryId, fixedAmount);
        Map<UUID, BigDecimal> occasionalByCategory = sumByCategory(occasional.stream(), type,
                Transaction::getType, Transaction::getCategoryId, Transaction::getAmount);

        Set<UUID> categoryIds = new LinkedHashSet<>(fixedByCategory.keySet());
        categoryIds.addAll(occasionalByCategory.keySet());

        return categoryIds.stream()
                .map(id -> {
                    Category category = categories.get(id);
                    BigDecimal f = fixedByCategory.getOrDefault(id, BigDecimal.ZERO);
                    BigDecimal o = occasionalByCategory.getOrDefault(id, BigDecimal.ZERO);
                    return new CategoryBreakdown(id, category.getName(), category.getColor(), f, o, f.add(o));
                })
                .sorted(Comparator.comparing(CategoryBreakdown::total).reversed())
                .toList();
    }

    private static <T> Map<UUID, BigDecimal> sumByCategory(Stream<T> items, EntryType type,
                                                           Function<T, EntryType> typeOf,
                                                           Function<T, UUID> categoryOf,
                                                           Function<T, BigDecimal> amountOf) {
        return items.filter(i -> typeOf.apply(i) == type)
                .collect(Collectors.groupingBy(categoryOf, HashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, amountOf, BigDecimal::add)));
    }

    private static <T> BigDecimal sum(Stream<T> items, EntryType type,
                                      Function<T, EntryType> typeOf, Function<T, BigDecimal> amountOf) {
        return items.filter(i -> typeOf.apply(i) == type).map(amountOf).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
