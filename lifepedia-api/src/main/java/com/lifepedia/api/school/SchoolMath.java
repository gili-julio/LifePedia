package com.lifepedia.api.school;

import com.lifepedia.api.school.SchoolCourse.Category;
import com.lifepedia.api.school.SchoolCourse.Status;
import com.lifepedia.api.school.SchoolEnrollment.Scale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Contas da Escola (puras): média e situação da matéria, nota da unidade pelas provas, índice e progresso do curso. */
public final class SchoolMath {

    private static final Set<String> CONCEPT_PASS = Set.of("A", "B", "C");

    private SchoolMath() {
    }

    /** Regra do vínculo (UFRN graduação: média 6, entre 5 e 6 aprova com toda unidade a partir de 3, frequência 75%). */
    public record Rule(Scale scale, BigDecimal passAverage, BigDecimal lowAverage, BigDecimal minUnit, int minAttendance) {
    }

    /** Prova ou trabalho com nota, para a média da unidade. */
    public record Graded(Integer unit, BigDecimal weight, BigDecimal grade) {
    }

    /**
     * @param partial  média das unidades com nota (nula sem nenhuma)
     * @param average  média final (todas as unidades com nota, reposição no lugar da menor)
     * @param outcome  situação pelas notas (nula enquanto falta unidade)
     * @param risk     abaixo da média para aprovar
     */
    public record Result(BigDecimal partial, BigDecimal average, Status outcome, boolean risk) {
    }

    /** Uma matéria para o índice e o progresso. */
    public record Taken(String period, Category category, Status status, Integer hours, BigDecimal average) {
    }

    public record PeriodAverage(String period, BigDecimal average, int hours, int courses) {
    }

    /**
     * @param termsLeft semestres que faltam no ritmo dos semestres já feitos (nulo sem histórico)
     */
    public record Progress(int mandatoryDone, int optionalDone, int complementaryDone, int ongoing, int totalDone, Integer totalRequired,
                           Integer termsLeft) {
    }

    /** Nota de cada unidade: a média ponderada das provas com nota daquela unidade, senão a digitada. */
    public static List<BigDecimal> units(List<BigDecimal> typed, List<Graded> assessments) {
        List<BigDecimal> result = new ArrayList<>(typed);
        for (int i = 0; i < result.size(); i++) {
            int unit = i + 1;
            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal weights = BigDecimal.ZERO;
            for (Graded g : assessments) {
                if (g.grade() == null || g.unit() == null || g.unit() != unit) continue;
                sum = sum.add(g.grade().multiply(g.weight()));
                weights = weights.add(g.weight());
            }
            if (weights.signum() > 0) result.set(i, sum.divide(weights, 2, RoundingMode.HALF_UP));
        }
        return result;
    }

    /** Média e situação pelas notas das unidades; conceito decide sozinho. */
    public static Result result(Rule rule, List<BigDecimal> units, BigDecimal recovery, String concept) {
        if (rule.scale() == Scale.CONCEPT) {
            if (concept == null || concept.isBlank()) return new Result(null, null, null, false);
            boolean pass = CONCEPT_PASS.contains(concept.trim().toUpperCase(Locale.ROOT));
            return new Result(null, null, pass ? Status.APR : Status.REP, !pass);
        }
        List<BigDecimal> graded = units.stream().filter(Objects::nonNull).toList();
        if (graded.isEmpty()) return new Result(null, null, null, false);
        BigDecimal partial = mean(graded);
        if (graded.size() < units.size()) return new Result(partial, null, null, partial.compareTo(rule.passAverage()) < 0);
        List<BigDecimal> finals = new ArrayList<>(graded);
        if (recovery != null) {
            int lowest = 0;
            for (int i = 1; i < finals.size(); i++) {
                if (finals.get(i).compareTo(finals.get(lowest)) < 0) lowest = i;
            }
            if (recovery.compareTo(finals.get(lowest)) > 0) finals.set(lowest, recovery);
        }
        BigDecimal average = mean(finals);
        BigDecimal min = finals.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        Status outcome;
        if (average.compareTo(rule.passAverage()) >= 0) {
            outcome = Status.APR;
        } else if (average.compareTo(rule.lowAverage()) >= 0 && min.compareTo(rule.minUnit()) >= 0) {
            outcome = Status.APRN;
        } else if (recovery == null) {
            outcome = Status.REC;
        } else {
            outcome = average.compareTo(rule.lowAverage()) >= 0 ? Status.REPN : Status.REP;
        }
        return new Result(partial, average, outcome, outcome != Status.APR && outcome != Status.APRN);
    }

    /** Índice: média final ponderada pela carga horária das matérias com média (aprovadas e reprovadas). */
    public static BigDecimal index(List<Taken> taken) {
        return weighted(taken.stream().filter(SchoolMath::counts).toList());
    }

    /** Índice de cada semestre, do mais antigo ao mais novo. */
    public static List<PeriodAverage> periods(List<Taken> taken) {
        Map<String, List<Taken>> byPeriod = new TreeMap<>();
        for (Taken t : taken) {
            if (t.period() != null) byPeriod.computeIfAbsent(t.period(), k -> new ArrayList<>()).add(t);
        }
        List<PeriodAverage> result = new ArrayList<>();
        byPeriod.forEach((period, list) -> {
            List<Taken> counted = list.stream().filter(SchoolMath::counts).toList();
            int hours = list.stream().filter(t -> t.status().passed()).mapToInt(t -> t.hours() == null ? 0 : t.hours()).sum();
            result.add(new PeriodAverage(period, weighted(counted), hours, list.size()));
        });
        return result;
    }

    /** Carga horária cumprida por parte e semestres que faltam no ritmo médio dos semestres com aprovação. */
    public static Progress progress(List<Taken> taken, Integer requiredMandatory, Integer requiredOptional, Integer requiredComplementary,
                                    int complementaryManual) {
        int mandatory = 0;
        int optional = 0;
        int complementary = complementaryManual;
        int ongoing = 0;
        Map<String, Integer> perPeriod = new TreeMap<>();
        for (Taken t : taken) {
            int hours = t.hours() == null ? 0 : t.hours();
            if (t.status().ongoing()) ongoing += hours;
            if (!t.status().passed()) continue;
            switch (t.category()) {
                case MANDATORY -> mandatory += hours;
                case OPTIONAL, ELECTIVE -> optional += hours;
                case COMPLEMENTARY -> complementary += hours;
            }
            if (t.period() != null) perPeriod.merge(t.period(), hours, Integer::sum);
        }
        Integer required = requiredMandatory == null && requiredOptional == null && requiredComplementary == null ? null
                : orZero(requiredMandatory) + orZero(requiredOptional) + orZero(requiredComplementary);
        int done = Math.min(mandatory, requiredMandatory == null ? mandatory : requiredMandatory)
                + Math.min(optional, requiredOptional == null ? optional : requiredOptional)
                + Math.min(complementary, requiredComplementary == null ? complementary : requiredComplementary);
        Integer termsLeft = null;
        int pace = perPeriod.isEmpty() ? 0 : perPeriod.values().stream().mapToInt(Integer::intValue).sum() / perPeriod.size();
        if (required != null && pace > 0) termsLeft = Math.max(0, (int) Math.ceil((double) Math.max(0, required - done) / pace));
        return new Progress(mandatory, optional, complementary, ongoing, done, required, termsLeft);
    }

    private static boolean counts(Taken t) {
        return t.status().graded() && t.average() != null && t.hours() != null && t.hours() > 0;
    }

    private static BigDecimal weighted(List<Taken> list) {
        BigDecimal sum = BigDecimal.ZERO;
        int hours = 0;
        for (Taken t : list) {
            sum = sum.add(t.average().multiply(BigDecimal.valueOf(t.hours())));
            hours += t.hours();
        }
        return hours == 0 ? null : sum.divide(BigDecimal.valueOf(hours), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
