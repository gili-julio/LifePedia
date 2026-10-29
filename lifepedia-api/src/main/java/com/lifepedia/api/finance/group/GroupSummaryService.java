package com.lifepedia.api.finance.group;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.category.Category;
import com.lifepedia.api.finance.category.CategoryRepository;
import com.lifepedia.api.finance.category.InvestmentCategories;
import com.lifepedia.api.finance.group.GroupFinanceDtos.GroupCategoryBreakdown;
import com.lifepedia.api.finance.group.GroupFinanceDtos.GroupSummary;
import com.lifepedia.api.finance.group.GroupFinanceDtos.MemberTotals;
import com.lifepedia.api.finance.group.GroupFinanceDtos.Reserve;
import com.lifepedia.api.finance.recurring.RecurringAdjustment;
import com.lifepedia.api.finance.recurring.RecurringAdjustmentRepository;
import com.lifepedia.api.finance.summary.SummaryDtos;
import com.lifepedia.api.finance.summary.SummaryDtos.CategoryItem;
import com.lifepedia.api.finance.summary.SummaryDtos.MonthPoint;
import com.lifepedia.api.finance.summary.SummaryDtos.Totals;
import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.planning.PlanSettings;
import com.lifepedia.api.planning.PlanSettingsRepository;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Métricas do grupo: mesmas regras do resumo pessoal, somando os itens de todos os membros. */
@Service
public class GroupSummaryService {

    private final GroupTransactionRepository transactions;
    private final GroupRecurringEntryRepository recurring;
    private final RecurringAdjustmentRepository adjustments;
    private final CategoryRepository categories;
    private final GroupMemberRepository members;
    private final UserRepository users;
    private final GroupAccess access;
    private final PlanSettingsRepository planSettings;

    public GroupSummaryService(GroupTransactionRepository transactions, GroupRecurringEntryRepository recurring,
                               RecurringAdjustmentRepository adjustments, CategoryRepository categories,
                               GroupMemberRepository members, UserRepository users, GroupAccess access,
                               PlanSettingsRepository planSettings) {
        this.transactions = transactions;
        this.recurring = recurring;
        this.adjustments = adjustments;
        this.categories = categories;
        this.members = members;
        this.users = users;
        this.access = access;
        this.planSettings = planSettings;
    }

    @Transactional(readOnly = true)
    public GroupSummary summary(UUID userId, UUID groupId, YearMonth month) {
        access.requireMember(groupId, userId);
        List<GroupRecurringEntry> allFixed = recurring.findByGroupId(groupId);
        List<GroupRecurringEntry> fixed = allFixed.stream().filter(e -> e.appliesTo(month)).toList();
        List<GroupTransaction> occasional = transactions.findByGroupIdAndMonth(groupId, month);
        Function<GroupRecurringEntry, BigDecimal> fixedAmount = amountIn(month, adjustmentsOf(fixed));
        Map<UUID, Category> categoryById = categoriesOf(fixed, occasional);
        Predicate<Object> investing = investing(categoryById);

        Totals fixedTotals = totals(fixed, fixedAmount, investing);
        Totals occasionalTotals = totals(occasional, GroupTransaction::getAmount, investing);
        Totals total = fixedTotals.plus(occasionalTotals);

        return new GroupSummary(month, fixedTotals, occasionalTotals, total,
                SummaryDtos.percent(fixedTotals.expense(), fixedTotals.income()),
                SummaryDtos.percent(total.invested(), total.income()),
                breakdown(fixed.stream().filter(investing.negate()).toList(), fixedAmount,
                        occasional.stream().filter(investing.negate()).toList(), EntryType.EXPENSE, categoryById),
                breakdown(fixed, fixedAmount, occasional, EntryType.INCOME, categoryById),
                memberTotals(groupId, fixed, fixedAmount, occasional, investing),
                reserve(groupId, month));
    }

    /** Série de {@code months} meses até {@code until}, do mais antigo ao mais novo. */
    @Transactional(readOnly = true)
    public List<MonthPoint> history(UUID userId, UUID groupId, YearMonth until, int months) {
        access.requireMember(groupId, userId);
        return points(groupId, until.minusMonths(months - 1L), until);
    }

    /** Série mês a mês de {@code from} até {@code until}. Sem checagem de acesso: quem chama já validou o membro. */
    @Transactional(readOnly = true)
    public List<MonthPoint> points(UUID groupId, YearMonth from, YearMonth until) {
        List<GroupRecurringEntry> fixed = recurring.findByGroupId(groupId);
        Map<UUID, Map<YearMonth, BigDecimal>> adjusted = adjustmentsOf(fixed);
        List<GroupTransaction> occasional = transactions.findByGroupIdAndMonthBetween(groupId, from, until);
        Predicate<Object> investing = investing(categoriesOf(fixed, occasional));

        List<MonthPoint> points = new ArrayList<>();
        for (YearMonth month = from; !month.isAfter(until); month = month.plusMonths(1)) {
            YearMonth current = month;
            List<GroupRecurringEntry> monthFixed = fixed.stream().filter(e -> e.appliesTo(current)).toList();
            List<GroupTransaction> monthOccasional = occasional.stream().filter(t -> t.getMonth().equals(current)).toList();
            Totals f = totals(monthFixed, amountIn(current, adjusted), investing);
            Totals o = totals(monthOccasional, GroupTransaction::getAmount, investing);
            Totals all = f.plus(o);
            points.add(new MonthPoint(current, f.income(), f.expense(), o.income(), o.expense(), all.invested(), all.balance()));
        }
        return points;
    }

    /** Itens de uma categoria (chave do resumo) no mês; ocasionais agrupados pela descrição. */
    @Transactional(readOnly = true)
    public List<CategoryItem> categoryItems(UUID userId, UUID groupId, String categoryKey, YearMonth month) {
        access.requireMember(groupId, userId);
        List<GroupRecurringEntry> fixed = recurring.findByGroupId(groupId).stream().filter(e -> e.appliesTo(month)).toList();
        List<GroupTransaction> occasional = transactions.findByGroupIdAndMonth(groupId, month);
        Map<UUID, Category> categoryById = categoriesOf(fixed, occasional);
        Function<GroupRecurringEntry, BigDecimal> fixedAmount = amountIn(month, adjustmentsOf(fixed));

        Stream<CategoryItem> fixedItems = fixed.stream()
                .filter(e -> categoryKey.equals(key(categoryById.get(e.getCategoryId()))))
                .map(e -> new CategoryItem(e.getDescription(), true, 1, fixedAmount.apply(e)));
        Map<String, List<GroupTransaction>> byDescription = occasional.stream()
                .filter(t -> categoryKey.equals(key(categoryById.get(t.getCategoryId()))))
                .collect(Collectors.groupingBy(t -> t.getDescription().trim().toLowerCase(Locale.ROOT),
                        LinkedHashMap::new, Collectors.toList()));
        Stream<CategoryItem> occasionalItems = byDescription.values().stream()
                .map(list -> new CategoryItem(list.getFirst().getDescription(), false, list.size(),
                        list.stream().map(GroupTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        return Stream.concat(fixedItems, occasionalItems)
                .sorted(Comparator.comparing(CategoryItem::total).reversed())
                .toList();
    }

    /** Reserva até o mês: valor do planejamento + investimentos lançados no grupo (fixos e ocasionais). */
    @Transactional(readOnly = true)
    public Reserve reserve(UUID groupId, YearMonth until) {
        BigDecimal manual = planSettings.findByGroupId(groupId).map(PlanSettings::getReserve).orElse(BigDecimal.ZERO);
        List<GroupRecurringEntry> fixed = recurring.findByGroupId(groupId);
        List<GroupTransaction> occasional = transactions.findByGroupIdAndMonthLessThanEqual(groupId, until);
        Predicate<Object> investing = investing(categoriesOf(fixed, occasional));
        Map<UUID, Map<YearMonth, BigDecimal>> adjusted = adjustmentsOf(fixed);

        BigDecimal invested = sum(occasional.stream().filter(investing).toList(), EntryType.EXPENSE, GroupTransaction::getAmount);
        for (GroupRecurringEntry e : fixed) {
            if (e.getType() != EntryType.EXPENSE || !investing.test(e)) continue;
            for (YearMonth m = e.getStartMonth(); !m.isAfter(until); m = m.plusMonths(1)) {
                if (e.getEndMonth() != null && m.isAfter(e.getEndMonth())) break;
                if (e.appliesTo(m)) invested = invested.add(amountIn(m, adjusted).apply(e));
            }
        }
        return Reserve.of(manual, invested);
    }

    /** Chave da categoria no grupo: tipo + nome (cada membro tem as suas categorias). */
    static String key(Category category) {
        return category.getType() + ":" + category.getName().trim().toLowerCase(Locale.ROOT);
    }

    private List<MemberTotals> memberTotals(UUID groupId, List<GroupRecurringEntry> fixed,
                                            Function<GroupRecurringEntry, BigDecimal> fixedAmount,
                                            List<GroupTransaction> occasional, Predicate<Object> investing) {
        List<UUID> memberIds = members.findByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .map(GroupMember::getUserId).toList();
        Map<UUID, User> byId = users.findAllById(memberIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return memberIds.stream().map(id -> {
                    List<GroupRecurringEntry> f = fixed.stream().filter(e -> e.getUserId().equals(id)).toList();
                    List<GroupTransaction> o = occasional.stream().filter(t -> t.getUserId().equals(id)).toList();
                    Totals t = totals(f, fixedAmount, investing).plus(totals(o, GroupTransaction::getAmount, investing));
                    return new MemberTotals(UserSummary.from(byId.get(id)), t.income(), t.expense(), t.invested(), t.balance());
                })
                .sorted(Comparator.comparing(MemberTotals::expense).reversed())
                .toList();
    }

    private static List<GroupCategoryBreakdown> breakdown(List<GroupRecurringEntry> fixed,
                                                          Function<GroupRecurringEntry, BigDecimal> fixedAmount,
                                                          List<GroupTransaction> occasional, EntryType type,
                                                          Map<UUID, Category> categoryById) {
        Map<String, Slice> slices = new LinkedHashMap<>();
        fixed.stream().filter(e -> e.getType() == type).forEach(e -> {
            Slice s = slice(slices, categoryById.get(e.getCategoryId()));
            s.fixed = s.fixed.add(fixedAmount.apply(e));
        });
        occasional.stream().filter(t -> t.getType() == type).forEach(t -> {
            Slice s = slice(slices, categoryById.get(t.getCategoryId()));
            s.occasional = s.occasional.add(t.getAmount());
        });
        return slices.entrySet().stream()
                .map(entry -> {
                    Slice s = entry.getValue();
                    return new GroupCategoryBreakdown(entry.getKey(), s.category.getName(), s.category.getColor(),
                            s.fixed, s.occasional, s.fixed.add(s.occasional));
                })
                .sorted(Comparator.comparing(GroupCategoryBreakdown::total).reversed())
                .toList();
    }

    /** Soma de uma categoria (por nome); nome e cor da primeira encontrada. */
    private static final class Slice {
        final Category category;
        BigDecimal fixed = BigDecimal.ZERO;
        BigDecimal occasional = BigDecimal.ZERO;

        Slice(Category category) {
            this.category = category;
        }
    }

    private static Slice slice(Map<String, Slice> slices, Category category) {
        return slices.computeIfAbsent(key(category), k -> new Slice(category));
    }

    private Map<UUID, Category> categoriesOf(List<GroupRecurringEntry> fixed, List<GroupTransaction> occasional) {
        var ids = new HashSet<UUID>();
        fixed.forEach(e -> ids.add(e.getCategoryId()));
        occasional.forEach(t -> ids.add(t.getCategoryId()));
        return categories.findAllById(ids).stream().collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    /** Ajustes mensais dos fixos pessoais de onde vêm os sincronizados. */
    private Map<UUID, Map<YearMonth, BigDecimal>> adjustmentsOf(List<GroupRecurringEntry> fixed) {
        List<UUID> sourceIds = fixed.stream().map(GroupRecurringEntry::getSourceId).filter(Objects::nonNull).toList();
        if (sourceIds.isEmpty()) return Map.of();
        return adjustments.findByRecurringEntryIdIn(sourceIds).stream().collect(Collectors.groupingBy(
                RecurringAdjustment::getRecurringEntryId,
                Collectors.toMap(RecurringAdjustment::getMonth, RecurringAdjustment::getAmount)));
    }

    /** Valor no mês: sincronizado usa o ajuste do fixo pessoal, se houver; cópia usa o valor salvo. */
    private static Function<GroupRecurringEntry, BigDecimal> amountIn(YearMonth month,
                                                                      Map<UUID, Map<YearMonth, BigDecimal>> adjusted) {
        return e -> e.getSourceId() == null ? e.getAmount()
                : adjusted.getOrDefault(e.getSourceId(), Map.of()).getOrDefault(month, e.getAmount());
    }

    /** Item (fixo ou ocasional) em categoria de investimento. */
    private static Predicate<Object> investing(Map<UUID, Category> categoryById) {
        return item -> InvestmentCategories.isInvestment(categoryById.get(item instanceof GroupTransaction t
                ? t.getCategoryId() : ((GroupRecurringEntry) item).getCategoryId()));
    }

    private static <T> Totals totals(List<T> items, Function<T, BigDecimal> amountOf, Predicate<Object> investing) {
        return Totals.of(sum(items, EntryType.INCOME, amountOf),
                sum(items.stream().filter(investing.negate()).toList(), EntryType.EXPENSE, amountOf),
                sum(items.stream().filter(investing).toList(), EntryType.EXPENSE, amountOf));
    }

    private static <T> BigDecimal sum(List<T> items, EntryType type, Function<T, BigDecimal> amountOf) {
        return items.stream()
                .filter(i -> typeOf(i) == type)
                .map(amountOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static EntryType typeOf(Object item) {
        return item instanceof GroupTransaction t ? t.getType() : ((GroupRecurringEntry) item).getType();
    }
}
