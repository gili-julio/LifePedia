package com.lifepedia.api.financing;

import com.lifepedia.api.financing.BcbClient.BankRate;
import com.lifepedia.api.financing.BcbClient.IndexPoint;
import com.lifepedia.api.financing.BcbClient.Published;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Taxas por banco e índices guardados do Banco Central; sem dados busca na hora (no máximo 1 vez por hora). */
@Service
public class FinancingRateService {

    private static final Duration RETRY = Duration.ofHours(1);
    /** O SGS recusa mais de 20 valores por consulta (HTTP 400). */
    private static final int INDEX_MONTHS = 20;

    /**
     * Taxas da modalidade em % ao mês.
     *
     * @param period   início da divulgação (nulo sem dados do Banco Central)
     * @param fallback sem dados: os três cenários usam a taxa padrão da modalidade
     */
    public record RateTable(Modality modality, LocalDate period, List<BankRate> banks, double low, double median,
                            double high, boolean fallback) {
    }

    private final FinancingRateRepository rates;
    private final IndexValueRepository indexes;
    private final BcbClient bcb;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Map<Object, Instant> lastTry = new ConcurrentHashMap<>();

    public FinancingRateService(FinancingRateRepository rates, IndexValueRepository indexes, BcbClient bcb,
                                PlatformTransactionManager transactions, Clock clock) {
        this.rates = rates;
        this.indexes = indexes;
        this.bcb = bcb;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    public void refreshAll() {
        for (Modality m : Modality.values()) refresh(m);
        for (CorrectionIndex i : CorrectionIndex.values()) refresh(i);
    }

    public RateTable table(Modality modality) {
        List<FinancingRate> stored = rates.findByModalityOrderByPositionAsc(modality);
        if (stored.isEmpty() && canTry(modality)) {
            refresh(modality);
            stored = rates.findByModalityOrderByPositionAsc(modality);
        }
        if (stored.isEmpty()) {
            double rate = modality.fallbackRate() / 100;
            return new RateTable(modality, null, List.of(), rate, rate, rate, true);
        }
        List<BankRate> banks = stored.stream()
                .map(r -> new BankRate(r.getPosition(), r.getBank(), r.getMonthlyRate(), r.getYearlyRate())).toList();
        double[] sorted = banks.stream().mapToDouble(b -> b.monthly().doubleValue() / 100).sorted().toArray();
        return new RateTable(modality, stored.getFirst().getPeriodStart(), banks,
                quantile(sorted, 0.25), quantile(sorted, 0.5), quantile(sorted, 0.75), false);
    }

    /** Variação média mensal (fração) dos últimos 12 meses guardados; padrão do índice sem dados. */
    public double projection(CorrectionIndex index) {
        if (index == CorrectionIndex.NONE) return 0;
        List<IndexValue> values = values(index);
        if (values.isEmpty()) return index.fallback() / 100;
        return values.subList(Math.max(0, values.size() - 12), values.size()).stream()
                .mapToDouble(v -> v.getRate().doubleValue()).average().orElse(index.fallback()) / 100;
    }

    /** Há valores do Banco Central guardados (senão a projeção é o padrão do índice). */
    public boolean known(CorrectionIndex index) {
        return index == CorrectionIndex.NONE || !values(index).isEmpty();
    }

    /** Correção de cada mês (fração): valor real do índice nos meses já divulgados, projeção nos outros. */
    public Function<YearMonth, Double> correction(CorrectionIndex index) {
        if (index == CorrectionIndex.NONE) return m -> 0d;
        Map<YearMonth, Double> known = values(index).stream()
                .collect(Collectors.toMap(IndexValue::getMonth, v -> v.getRate().doubleValue() / 100));
        double projected = projection(index);
        YearMonth current = YearMonth.now(clock);
        return m -> m.isBefore(current) ? known.getOrDefault(m, projected) : projected;
    }

    private List<IndexValue> values(CorrectionIndex index) {
        List<IndexValue> stored = indexes.findByIndexOrderByMonthAsc(index);
        if (stored.isEmpty() && canTry(index)) {
            refresh(index);
            stored = indexes.findByIndexOrderByMonthAsc(index);
        }
        return stored;
    }

    private void refresh(Modality modality) {
        lastTry.put(modality, Instant.now(clock));
        Optional<Published> published = bcb.rates(modality);
        if (published.isEmpty()) return;
        transaction.executeWithoutResult(status -> {
            rates.deleteByModality(modality);
            rates.saveAll(published.get().banks().stream()
                    .map(b -> new FinancingRate(modality, published.get().period(), b.position(), b.bank(),
                            b.monthly(), b.yearly()))
                    .toList());
        });
    }

    private void refresh(CorrectionIndex index) {
        if (index == CorrectionIndex.NONE) return;
        lastTry.put(index, Instant.now(clock));
        List<IndexPoint> points = bcb.index(index, INDEX_MONTHS);
        if (points.isEmpty()) return;
        transaction.executeWithoutResult(status -> {
            Map<YearMonth, IndexValue> stored = indexes.findByIndexOrderByMonthAsc(index).stream()
                    .collect(Collectors.toMap(IndexValue::getMonth, Function.identity()));
            for (IndexPoint p : points) {
                IndexValue value = stored.get(p.month());
                if (value == null) indexes.save(new IndexValue(index, p.month(), p.rate()));
                else if (value.getRate().compareTo(p.rate()) != 0) value.setRate(p.rate());
            }
        });
    }

    private boolean canTry(Object key) {
        Instant last = lastTry.get(key);
        return last == null || last.plus(RETRY).isBefore(Instant.now(clock));
    }

    private static double quantile(double[] sorted, double q) {
        if (sorted.length == 1) return sorted[0];
        double position = q * (sorted.length - 1);
        int below = (int) Math.floor(position);
        int above = Math.min(sorted.length - 1, below + 1);
        return sorted[below] + (sorted[above] - sorted[below]) * (position - below);
    }
}
