package com.lifepedia.api.planning;

import com.lifepedia.api.finance.group.GroupFinanceDtos.Reserve;
import com.lifepedia.api.finance.group.GroupSummaryService;
import com.lifepedia.api.finance.summary.SummaryDtos.MonthPoint;
import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.planning.PlanningDtos.Base;
import com.lifepedia.api.planning.PlanningDtos.ItemResult;
import com.lifepedia.api.planning.PlanningDtos.Outcome;
import com.lifepedia.api.planning.PlanningDtos.PlanItemResponse;
import com.lifepedia.api.planning.PlanningDtos.ScenarioComparison;
import com.lifepedia.api.planning.PlanningDtos.Simulation;
import com.lifepedia.api.planning.PlanningDtos.TimelinePoint;
import com.lifepedia.api.planning.PlanningSimulator.Entry;
import com.lifepedia.api.planning.PlanningSimulator.ItemOutcome;
import com.lifepedia.api.planning.PlanningSimulator.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Simulação do planejamento. Saldo previsto de cada mês = fixos do grupo naquele mês + média dos ocasionais
 * (ganhos − despesas) dos últimos N meses completos, ou o valor manual; mais o "e se" da tela.
 */
@Service
public class PlanningSimulationService {

    private final PlanItemRepository items;
    private final PlanScenarioRepository scenarios;
    private final PlanSettingsRepository settingsRepository;
    private final PlanningService planning;
    private final GroupSummaryService groupSummary;
    private final GroupAccess access;

    public PlanningSimulationService(PlanItemRepository items, PlanScenarioRepository scenarios,
                                     PlanSettingsRepository settingsRepository, PlanningService planning,
                                     GroupSummaryService groupSummary, GroupAccess access) {
        this.items = items;
        this.scenarios = scenarios;
        this.settingsRepository = settingsRepository;
        this.planning = planning;
        this.groupSummary = groupSummary;
        this.access = access;
    }

    /** @param scenarioId simula os itens do cenário em vez dos ativos */
    @Transactional(readOnly = true)
    public Simulation simulate(UUID userId, UUID groupId, UUID scenarioId, BigDecimal extraIncome, BigDecimal extraExpense) {
        GroupMember me = access.requireMember(groupId, userId);
        Context ctx = context(groupId, extraIncome, extraExpense);
        Set<UUID> considered = scenarioId == null ? activeIds(ctx.pending)
                : planning.findScenario(groupId, scenarioId).getItemIds();
        Result result = run(ctx, considered);

        List<PlanItemResponse> pendingResponses = planning.responses(ctx.pending, me);
        List<ItemResult> results = new ArrayList<>();
        for (PlanItemResponse item : pendingResponses) {
            ItemOutcome o = result.items().get(item.id());
            results.add(new ItemResult(item, considered.contains(item.id()), o.shortfall(), o.earliest(),
                    o.monthlySaving(), o.installment(), o.overdue()));
        }
        List<TimelinePoint> timeline = result.timeline().stream()
                .map(p -> new TimelinePoint(p.month(), p.base(), p.planned(), p.withoutPlan(), p.balance(), p.due()))
                .toList();
        return new Simulation(ctx.start, scenarioId, PlanningService.settingsOf(ctx.settings, ctx.reserve), ctx.baseInfo,
                outcome(result), timeline, results, planning.responses(ctx.acquired, me));
    }

    /** Resultado dos itens ativos e de cada cenário, lado a lado. */
    @Transactional(readOnly = true)
    public List<ScenarioComparison> compare(UUID userId, UUID groupId, BigDecimal extraIncome, BigDecimal extraExpense) {
        access.requireMember(groupId, userId);
        Context ctx = context(groupId, extraIncome, extraExpense);
        List<ScenarioComparison> list = new ArrayList<>();
        list.add(new ScenarioComparison(null, "Itens ativos", outcome(run(ctx, activeIds(ctx.pending)))));
        for (PlanScenario s : scenarios.findByGroupIdOrderByCreatedAtAsc(groupId)) {
            list.add(new ScenarioComparison(s.getId(), s.getName(), outcome(run(ctx, s.getItemIds()))));
        }
        return list;
    }

    private record Context(YearMonth start, PlanSettings settings, Reserve reserve, List<PlanItem> pending,
                           List<PlanItem> acquired, List<BigDecimal> base, int display, Base baseInfo) {
    }

    private Context context(UUID groupId, BigDecimal extraIncome, BigDecimal extraExpense) {
        YearMonth start = YearMonth.now();
        PlanSettings settings = settingsRepository.findByGroupId(groupId).orElseGet(() -> new PlanSettings(groupId));
        List<PlanItem> all = items.findByGroupIdOrderByPositionAscCreatedAtAsc(groupId);
        List<PlanItem> pending = all.stream().filter(i -> !i.isAcquired()).toList();
        List<PlanItem> acquired = all.stream().filter(PlanItem::isAcquired)
                .sorted((a, b) -> b.getAcquiredOn().compareTo(a.getAcquiredOn())).toList();

        YearMonth shown = PlanningSimulator.horizon(start, entries(pending, Set.of()));
        int display = (shown.getYear() - start.getYear()) * 12 + shown.getMonthValue() - start.getMonthValue() + 1;
        List<MonthPoint> future = groupSummary.points(groupId, start, shown.plusMonths(PlanningSimulator.LOOKAHEAD_MONTHS));
        int months = settings.getAverageMonths();
        List<MonthPoint> basis = months > 0 ? averageBasis(groupId, start, months) : List.of();
        BigDecimal occasionalAverage = basis.isEmpty() ? BigDecimal.ZERO : basis.stream()
                .map(p -> p.occasionalIncome().subtract(p.occasionalExpense()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(basis.size()), 2, RoundingMode.HALF_UP);
        BigDecimal extra = PlanningService.zeroIfNull(extraIncome).subtract(PlanningService.zeroIfNull(extraExpense));
        BigDecimal manual = settings.getManualBalance();
        List<BigDecimal> base = new ArrayList<>(future.size());
        for (MonthPoint p : future) {
            BigDecimal value = manual != null ? manual
                    : p.fixedIncome().subtract(p.fixedExpense()).add(occasionalAverage);
            base.add(value.add(extra));
        }
        MonthPoint first = future.getFirst();
        Base info = new Base(first.fixedIncome(), first.fixedExpense(), occasionalAverage, months,
                basis.stream().map(MonthPoint::month).toList(), manual, extra, base.getFirst());
        return new Context(start, settings, groupSummary.reserve(groupId, start), pending, acquired, base, display, info);
    }

    /**
     * Meses da média: os completos da janela que têm ocasionais (mês sem nada não puxa a média para baixo).
     * Sem nenhum, o mês atual (grupo novo ou só com o mês corrente lançado).
     */
    private List<MonthPoint> averageBasis(UUID groupId, YearMonth start, int months) {
        List<MonthPoint> complete = groupSummary.points(groupId, start.minusMonths(months), start.minusMonths(1)).stream()
                .filter(PlanningSimulationService::hasOccasional).toList();
        if (!complete.isEmpty()) return complete;
        return groupSummary.points(groupId, start, start).stream().filter(PlanningSimulationService::hasOccasional).toList();
    }

    private static boolean hasOccasional(MonthPoint p) {
        return p.occasionalIncome().signum() != 0 || p.occasionalExpense().signum() != 0;
    }

    private static Result run(Context ctx, Set<UUID> considered) {
        return PlanningSimulator.run(ctx.start, ctx.base, ctx.reserve.total(), entries(ctx.pending, considered),
                ctx.display);
    }

    private static List<Entry> entries(List<PlanItem> pending, Set<UUID> considered) {
        return pending.stream().map(i -> new Entry(i.getId(), i.getAmount(), i.getTargetMonth(), i.getInstallments(),
                considered.contains(i.getId()))).toList();
    }

    private static Set<UUID> activeIds(List<PlanItem> pending) {
        return pending.stream().filter(PlanItem::isActive).map(PlanItem::getId).collect(Collectors.toSet());
    }

    private static Outcome outcome(Result r) {
        return new Outcome(r.count(), r.total(), r.shortfall(), r.lowestBalance(), r.lowestMonth(), r.endBalance(),
                r.endMonth());
    }
}
