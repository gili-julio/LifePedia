package com.lifepedia.api.finance.simulation;

import com.lifepedia.api.finance.simulation.FinanceSimulator.Invest;
import com.lifepedia.api.finance.simulation.FinanceSimulator.Market;
import com.lifepedia.api.finance.simulation.FinanceSimulator.Outcome;
import com.lifepedia.api.finance.simulation.FinanceSimulator.Plan;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Base;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Change;
import com.lifepedia.api.finance.simulation.SimulatorDtos.CompareItem;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Input;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Investment;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Rates;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Result;
import com.lifepedia.api.finance.simulation.SimulatorDtos.SaveRequest;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Saved;
import com.lifepedia.api.finance.simulation.SimulatorDtos.SavedData;
import com.lifepedia.api.finance.simulation.SimulatorDtos.Yield;
import com.lifepedia.api.finance.summary.SummaryDtos.MonthPoint;
import com.lifepedia.api.finance.summary.SummaryService;
import com.lifepedia.api.financing.MarketRates;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Simulador do financeiro pessoal: base nos fixos e na média dos ocasionais, ganhos e gastos livres e investimento. */
@Service
public class SimulatorService {

    static final int MAX_SAVED = 50;
    private static final Set<Integer> AVERAGE_MONTHS = Set.of(0, 3, 6, 12);

    private final FinanceSimulationRepository simulations;
    private final SummaryService summary;
    private final MarketRates market;
    private final RateLimiter rateLimiter;
    private final JsonMapper json;
    private final Clock clock;

    public SimulatorService(FinanceSimulationRepository simulations, SummaryService summary, MarketRates market,
                            RateLimiter rateLimiter, JsonMapper json, Clock clock) {
        this.simulations = simulations;
        this.summary = summary;
        this.market = market;
        this.rateLimiter = rateLimiter;
        this.json = json;
        this.clock = clock;
    }

    /** Fora de transação: as taxas podem ser buscadas no Banco Central na hora. */
    public Result run(UUID userId, Input input, boolean real) {
        requireRun(userId);
        validate(input);
        return simulate(userId, input, real, market.current());
    }

    public List<CompareItem> compare(UUID userId, List<UUID> ids, boolean real) {
        requireRun(userId);
        Map<UUID, FinanceSimulation> found = simulations.findByUserIdAndIdIn(userId, ids).stream()
                .collect(Collectors.toMap(FinanceSimulation::getId, Function.identity()));
        if (found.size() != Set.copyOf(ids).size()) throw new NotFoundException("Simulação não encontrada.");
        MarketRates.Rates rates = market.current();
        return ids.stream().distinct().map(found::get).map(s -> {
            Result result = simulate(userId, input(s), real, rates);
            return new CompareItem(s.getId(), s.getName(), result.summary(), result.points());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Saved> saved(UUID userId) {
        return simulations.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::saved).toList();
    }

    @Transactional
    public Saved save(UUID userId, SaveRequest request) {
        validate(request.input());
        if (simulations.countByUserId(userId) >= MAX_SAVED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_SAVED + " simulações salvas.");
        }
        return saved(simulations.save(new FinanceSimulation(userId, request.name().trim(), json.writeValueAsString(request.input()))));
    }

    @Transactional
    public Saved update(UUID userId, UUID id, SaveRequest request) {
        validate(request.input());
        FinanceSimulation simulation = find(userId, id);
        simulation.set(request.name().trim(), json.writeValueAsString(request.input()));
        return saved(simulation);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        simulations.delete(find(userId, id));
    }

    @Transactional(readOnly = true)
    public List<SavedData> exportData(UUID userId) {
        return simulations.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(s -> new SavedData(s.getName(), input(s), s.getCreatedAt())).toList();
    }

    private Result simulate(UUID userId, Input input, boolean real, MarketRates.Rates rates) {
        YearMonth start = YearMonth.now(clock);
        int months = input.months();
        List<MonthPoint> future = summary.history(userId, start.plusMonths(months - 1L), months);
        List<MonthPoint> basis = input.averageMonths() > 0 ? averageBasis(userId, start, input.averageMonths()) : List.of();
        BigDecimal occasional = basis.isEmpty() ? BigDecimal.ZERO : basis.stream()
                .map(p -> p.occasionalIncome().subtract(p.occasionalExpense()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(basis.size()), 2, RoundingMode.HALF_UP);
        boolean fixed = Boolean.TRUE.equals(input.useFixed());
        double[] base = new double[months];
        for (int t = 0; t < months; t++) {
            MonthPoint p = future.get(t);
            BigDecimal value = fixed ? p.fixedIncome().subtract(p.fixedExpense()).add(occasional) : occasional;
            base[t] = value.doubleValue();
        }
        Market values = new Market(rates.cdi(), rates.selic(), rates.ipca(), rates.tr());
        Outcome outcome = FinanceSimulator.run(new Plan(start, input.startBalance().doubleValue(), base, input.changes(),
                invest(input.investment(), values), rates.ipca(), real));
        MonthPoint first = future.getFirst();
        Base info = new Base(fixed ? first.fixedIncome() : BigDecimal.ZERO, fixed ? first.fixedExpense() : BigDecimal.ZERO,
                occasional, basis.stream().map(MonthPoint::month).toList(), BigDecimal.valueOf(base[0]).setScale(2, RoundingMode.HALF_UP));
        Rates shown = new Rates(FinanceSimulator.percent(FinanceSimulator.yearly(rates.cdi())),
                FinanceSimulator.percent(FinanceSimulator.yearly(rates.selic())),
                FinanceSimulator.percent(FinanceSimulator.yearly(rates.ipca())), rates.fallback());
        return new Result(start, real, info, shown, outcome.summary(), outcome.points());
    }

    /** Meses da média: os completos da janela com ocasionais; sem nenhum, o mês atual (como no planejamento do grupo). */
    private List<MonthPoint> averageBasis(UUID userId, YearMonth start, int months) {
        List<MonthPoint> complete = summary.history(userId, start.minusMonths(1), months).stream()
                .filter(SimulatorService::hasOccasional).toList();
        if (!complete.isEmpty()) return complete;
        return summary.history(userId, start, 1).stream().filter(SimulatorService::hasOccasional).toList();
    }

    private Saved saved(FinanceSimulation s) {
        return new Saved(s.getId(), s.getName(), input(s), s.getCreatedAt(), s.getUpdatedAt());
    }

    private Input input(FinanceSimulation s) {
        try {
            return json.readValue(s.getInput(), Input.class);
        } catch (JacksonException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Simulação salva ilegível.");
        }
    }

    private FinanceSimulation find(UUID userId, UUID id) {
        return simulations.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Simulação não encontrada."));
    }

    private void requireRun(UUID userId) {
        if (!rateLimiter.tryAcquire("finance-simulator:" + userId, 120, Duration.ofMinutes(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitas simulações seguidas. Espere um pouco.");
        }
    }

    private static void validate(Input input) {
        if (!AVERAGE_MONTHS.contains(input.averageMonths())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Média dos ocasionais inválida.");
        }
        if (input.changes() != null) {
            for (Change c : input.changes()) {
                if (c.end() != null && c.end().isBefore(c.start())) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "\"" + c.name().trim() + "\" termina antes de começar.");
                }
            }
        }
        Investment inv = input.investment();
        if (inv != null && inv.source() != Yield.SAVINGS && inv.rate() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Informe a taxa do investimento.");
        }
    }

    private static Invest invest(Investment inv, Market market) {
        if (inv == null) return null;
        double rate = FinanceSimulator.monthlyRate(inv.source(), inv.rate() == null ? 0 : inv.rate().doubleValue(), market);
        return new Invest(inv.initial().doubleValue(), inv.monthly().doubleValue(), rate,
                inv.source() != Yield.SAVINGS && Boolean.TRUE.equals(inv.tax()), Boolean.TRUE.equals(inv.fromBalance()));
    }

    private static boolean hasOccasional(MonthPoint p) {
        return p.occasionalIncome().signum() != 0 || p.occasionalExpense().signum() != 0;
    }
}
