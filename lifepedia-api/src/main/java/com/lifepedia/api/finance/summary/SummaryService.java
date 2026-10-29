package com.lifepedia.api.finance.summary;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.category.Category;
import com.lifepedia.api.finance.category.CategoryService;
import com.lifepedia.api.finance.category.InvestmentCategories;
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
import java.util.function.Predicate;
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
        Map<UUID, Category> categories = categoryService.mapByIdFor(userId);
        Set<UUID> investing = InvestmentCategories.ids(categories.values());

        Totals fixedTotals = totals(fixed, RecurringEntry::getType, RecurringEntry::getCategoryId, fixedAmount, investing);
        Totals occasionalTotals = totals(occasional, Transaction::getType, Transaction::getCategoryId,
                Transaction::getAmount, investing);
        Totals total = fixedTotals.plus(occasionalTotals);

        return new MonthlySummary(month, fixedTotals, occasionalTotals, total,
                SummaryDtos.percent(fixedTotals.expense(), fixedTotals.income()),
                SummaryDtos.percent(total.invested(), total.income()),
                breakdown(fixed, fixedAmount, occasional, EntryType.EXPENSE, categories).stream()
                        .filter(c -> !investing.contains(c.categoryId())).toList(),
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
        Set<UUID> investing = InvestmentCategories.ids(categoryService.mapByIdFor(userId).values());

        List<MonthPoint> points = new ArrayList<>(months);
        for (YearMonth month = from; !month.isAfter(until); month = month.plusMonths(1)) {
            YearMonth current = month;
            List<RecurringEntry> monthFixed = fixed.stream().filter(e -> e.appliesTo(current)).toList();
            List<Transaction> monthOccasional = occasional.stream()
                    .filter(t -> t.getMonth().equals(current)).toList();

            Function<RecurringEntry, BigDecimal> fixedAmount = amountIn(current, adjustments);
            Totals f = totals(monthFixed, RecurringEntry::getType, RecurringEntry::getCategoryId, fixedAmount, investing);
            Totals o = totals(monthOccasional, Transaction::getType, Transaction::getCategoryId, Transaction::getAmount,
                    investing);
            Totals all = f.plus(o);
            points.add(new MonthPoint(current, f.income(), f.expense(), o.income(), o.expense(), all.invested(), all.balance()));
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

    /** Ganhos, despesas e investido (despesa em categoria de investimento) da lista. */
    private static <T> Totals totals(List<T> items, Function<T, EntryType> typeOf, Function<T, UUID> categoryOf,
                                     Function<T, BigDecimal> amountOf, Set<UUID> investing) {
        Predicate<T> invested = i -> investing.contains(categoryOf.apply(i));
        return Totals.of(sum(items.stream(), EntryType.INCOME, typeOf, amountOf),
                sum(items.stream().filter(invested.negate()), EntryType.EXPENSE, typeOf, amountOf),
                sum(items.stream().filter(invested), EntryType.EXPENSE, typeOf, amountOf));
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
