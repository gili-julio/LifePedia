package com.lifepedia.api.financing;

import com.lifepedia.api.financing.BcbClient.BankRate;
import com.lifepedia.api.financing.FinancingDtos.BankOption;
import com.lifepedia.api.financing.FinancingDtos.CorrectionInfo;
import com.lifepedia.api.financing.FinancingDtos.CostItem;
import com.lifepedia.api.financing.FinancingDtos.CostTotals;
import com.lifepedia.api.financing.FinancingDtos.CostWhen;
import com.lifepedia.api.financing.FinancingDtos.IncomeCheck;
import com.lifepedia.api.financing.FinancingDtos.RateSource;
import com.lifepedia.api.financing.FinancingDtos.RateSuggestion;
import com.lifepedia.api.financing.FinancingDtos.Scenario;
import com.lifepedia.api.financing.FinancingDtos.ScenarioResult;
import com.lifepedia.api.financing.FinancingDtos.SimulationRequest;
import com.lifepedia.api.financing.FinancingDtos.SimulationResponse;
import com.lifepedia.api.financing.FinancingDtos.SystemResult;
import com.lifepedia.api.financing.FinancingDtos.TermRow;
import com.lifepedia.api.financing.FinancingMath.AmortizationSystem;
import com.lifepedia.api.financing.FinancingMath.Plan;
import com.lifepedia.api.financing.FinancingMath.Row;
import com.lifepedia.api.financing.FinancingMath.Totals;
import com.lifepedia.api.financing.FinancingRateService.RateTable;
import com.lifepedia.api.shared.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;

/** Simulação sem pedir juros: cenários pelas taxas dos bancos, SAC x Price, prazos, gastos, CET e renda. */
@Service
public class FinancingSimulator {

    private static final BigDecimal INCOME_LIMIT = BigDecimal.valueOf(30);
    private static final BigDecimal HOUSING_MAX_SHARE = new BigDecimal("0.8");

    private final FinancingRateService rates;
    private final Clock clock;

    public FinancingSimulator(FinancingRateService rates, Clock clock) {
        this.rates = rates;
        this.clock = clock;
    }

    /** Correção padrão do tipo: imóvel com taxa de mercado corrige pela TR. */
    public static CorrectionIndex defaultIndex(FinancingKind kind) {
        return kind.isHousing() ? CorrectionIndex.TR : CorrectionIndex.NONE;
    }

    public RateSuggestion suggestion(FinancingKind kind) {
        RateTable table = rates.table(kind.modality());
        return new RateSuggestion(source(table), FinancingMath.percent(table.low()), FinancingMath.percent(table.median()),
                FinancingMath.percent(table.high()), defaultIndex(kind));
    }

    public SimulationResponse simulate(SimulationRequest request) {
        FinancingKind kind = request.kind();
        BigDecimal fgts = kind.isHousing() && request.fgts() != null ? request.fgts() : BigDecimal.ZERO;
        BigDecimal base = request.price().subtract(request.downPayment()).subtract(fgts);
        if (base.signum() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "A entrada cobre o preço todo: não há o que financiar.");
        }
        int months = request.months() == null ? kind.defaultMonths() : request.months();
        if (months > kind.maxMonths()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Prazo máximo para este tipo: " + kind.maxMonths() + " meses.");
        }
        YearMonth start = request.startMonth() == null ? YearMonth.now(clock) : request.startMonth();
        YearMonth firstMonth = start.plusMonths(1);

        List<CostItem> costs = FinancingCosts.apply(
                FinancingCosts.defaults(kind, request.condition(), request.price(), base, months), request.costs());
        BigDecimal financedCosts = sum(costs, CostWhen.FINANCED);
        BigDecimal financed = base.add(financedCosts);
        double insuranceRate = costs.stream().filter(c -> FinancingCosts.MIP.equals(c.key()))
                .mapToDouble(c -> c.amount().doubleValue() / financed.doubleValue()).sum();
        double monthlyFee = costs.stream()
                .filter(c -> c.when() == CostWhen.INSTALLMENT && !FinancingCosts.MIP.equals(c.key()))
                .mapToDouble(c -> c.amount().doubleValue()).sum();
        BigDecimal bankUpfront = costs.stream().filter(c -> c.when() == CostWhen.UPFRONT && c.bank())
                .map(CostItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        double received = base.subtract(bankUpfront).doubleValue();

        CorrectionIndex index = defaultIndex(kind);
        Function<YearMonth, Double> correction = rates.correction(index);
        Plan plan = new Plan(financed.doubleValue(), 0, months, AmortizationSystem.PRICE, insuranceRate, monthlyFee,
                firstMonth, correction, List.of());

        RateTable table = rates.table(kind.modality());
        List<ScenarioResult> scenarios = new ArrayList<>();
        if (request.customRate() != null) {
            scenarios.add(scenario(Scenario.CUSTOM, request.customRate().doubleValue() / 100, plan, received));
        }
        scenarios.add(scenario(Scenario.LOW, table.low(), plan, received));
        scenarios.add(scenario(Scenario.MEDIAN, table.median(), plan, received));
        scenarios.add(scenario(Scenario.HIGH, table.high(), plan, received));

        Scenario chosen = request.scenario() != null && (request.scenario() != Scenario.CUSTOM || request.customRate() != null)
                ? request.scenario() : request.customRate() != null ? Scenario.CUSTOM : Scenario.MEDIAN;
        AmortizationSystem system = request.system() != null ? request.system()
                : kind.isHousing() ? AmortizationSystem.SAC : AmortizationSystem.PRICE;
        double rate = rateOf(chosen, request, table);
        Plan selected = plan.with(rate, months, system);
        List<Row> schedule = FinancingMath.schedule(selected);

        double referenceRate = request.customRate() != null ? rate(request) : table.median();
        List<TermRow> terms = terms(kind, months, plan, referenceRate);

        BigDecimal upfrontCosts = sum(costs, CostWhen.UPFRONT);
        CostTotals totals = new CostTotals(request.downPayment().add(fgts).add(upfrontCosts), upfrontCosts, financedCosts,
                schedule.isEmpty() ? BigDecimal.ZERO : schedule.getFirst().charges(), sum(costs, CostWhen.YEARLY),
                sum(costs, CostWhen.MONTHLY));

        IncomeCheck income = income(request, schedule, selected, fgts, financedCosts);
        List<BankOption> banks = table.banks().stream().map(b -> bank(b, selected)).toList();

        List<String> warnings = new ArrayList<>();
        if (table.fallback()) {
            warnings.add("Sem conexão com o Banco Central agora: usamos uma taxa média aproximada.");
        }
        if (kind.isHousing() && base.compareTo(request.price().multiply(HOUSING_MAX_SHARE)) > 0) {
            warnings.add("Os bancos costumam financiar até 80% do imóvel: a entrada (com FGTS) costuma ser de 20% ou mais.");
        }
        if (income != null && income.percent().compareTo(INCOME_LIMIT) > 0) {
            warnings.add("A parcela passa de 30% da renda: o banco pode não aprovar.");
        }
        return new SimulationResponse(source(table), base, financed, months, firstMonth,
                index == CorrectionIndex.NONE ? null : new CorrectionInfo(index, FinancingMath.percent(rates.projection(index))),
                scenarios, terms, costs, totals, income, banks, chosen, system, schedule, warnings);
    }

    private static ScenarioResult scenario(Scenario scenario, double rate, Plan plan, double received) {
        return new ScenarioResult(scenario, FinancingMath.percent(rate), FinancingMath.percent(FinancingMath.yearly(rate)),
                result(plan.with(rate, plan.months(), AmortizationSystem.SAC), received),
                result(plan.with(rate, plan.months(), AmortizationSystem.PRICE), received));
    }

    private static SystemResult result(Plan plan, double received) {
        List<Row> rows = FinancingMath.schedule(plan);
        Totals totals = FinancingMath.totals(rows);
        Double cet = FinancingMath.cet(received, rows);
        return new SystemResult(totals.firstPayment(), totals.lastPayment(), totals.totalPaid(), totals.totalInterest(),
                cet == null ? null : FinancingMath.percent(cet), cet == null ? null : FinancingMath.percent(FinancingMath.yearly(cet)));
    }

    private static List<TermRow> terms(FinancingKind kind, int months, Plan plan, double rate) {
        TreeSet<Integer> options = new TreeSet<>(kind.terms());
        options.add(months);
        return options.stream().filter(m -> m <= kind.maxMonths()).map(m -> {
            Totals price = FinancingMath.totals(FinancingMath.schedule(plan.with(rate, m, AmortizationSystem.PRICE)));
            Totals sac = FinancingMath.totals(FinancingMath.schedule(plan.with(rate, m, AmortizationSystem.SAC)));
            return new TermRow(m, price.firstPayment(), price.totalInterest(), sac.firstPayment(), sac.lastPayment(),
                    sac.totalInterest());
        }).toList();
    }

    private static IncomeCheck income(SimulationRequest request, List<Row> schedule, Plan plan, BigDecimal fgts,
                                      BigDecimal financedCosts) {
        if (request.income() == null || request.income().signum() <= 0 || schedule.isEmpty()) return null;
        BigDecimal payment = schedule.getFirst().payment();
        BigDecimal percent = payment.multiply(BigDecimal.valueOf(100)).divide(request.income(), 2, RoundingMode.HALF_UP);
        BigDecimal maxPayment = request.income().multiply(INCOME_LIMIT).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        double maxLoan = Math.max(0, (maxPayment.doubleValue() - plan.monthlyFee()) / FinancingMath.firstPaymentFactor(plan));
        BigDecimal maxPrice = FinancingMath.money(maxLoan).subtract(financedCosts).max(BigDecimal.ZERO)
                .add(request.downPayment()).add(fgts);
        return new IncomeCheck(request.income(), payment, percent, INCOME_LIMIT, maxPayment, maxPrice);
    }

    private static BankOption bank(BankRate bank, Plan plan) {
        List<Row> rows = FinancingMath.schedule(plan.with(bank.monthly().doubleValue() / 100, plan.months(), plan.system()));
        return new BankOption(bank.position(), bank.bank(), bank.monthly(), bank.yearly(),
                rows.isEmpty() ? BigDecimal.ZERO : rows.getFirst().payment());
    }

    private static double rateOf(Scenario scenario, SimulationRequest request, RateTable table) {
        return switch (scenario) {
            case CUSTOM -> rate(request);
            case LOW -> table.low();
            case MEDIAN -> table.median();
            case HIGH -> table.high();
        };
    }

    private static double rate(SimulationRequest request) {
        return request.customRate().doubleValue() / 100;
    }

    private static RateSource source(RateTable table) {
        return new RateSource(table.modality(), table.modality().label(), table.period(), table.banks().size(),
                table.fallback());
    }

    private static BigDecimal sum(List<CostItem> costs, CostWhen when) {
        return costs.stream().filter(c -> c.when() == when).map(CostItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
