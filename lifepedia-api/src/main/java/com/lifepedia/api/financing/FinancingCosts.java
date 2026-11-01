package com.lifepedia.api.financing;

import com.lifepedia.api.financing.FinancingDtos.CostItem;
import com.lifepedia.api.financing.FinancingDtos.CostWhen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Gastos padrão estimados de cada tipo de bem (médias de mercado; variam por estado, cidade e banco). */
final class FinancingCosts {

    static final String MIP = "mip";
    private static final BigDecimal MIP_RATE = new BigDecimal("0.00025");

    private FinancingCosts() {
    }

    /** @param base valor a financiar antes dos custos financiados */
    static List<CostItem> defaults(FinancingKind kind, ItemCondition condition, BigDecimal price, BigDecimal base, int months) {
        List<CostItem> costs = new ArrayList<>();
        boolean used = condition == ItemCondition.USED;
        if (kind.isHousing()) {
            costs.add(cost("itbi", "ITBI (imposto da prefeitura)", pct(price, "3"), CostWhen.UPFRONT, false,
                    "De 2% a 3% do valor, conforme a cidade"));
            costs.add(cost("registry", "Registro em cartório", pct(price, "1"), CostWhen.UPFRONT, false,
                    "Registro do contrato e da matrícula"));
            costs.add(cost("appraisal", "Avaliação do imóvel", new BigDecimal("3000"), CostWhen.UPFRONT, true,
                    "Tarifa cobrada pelo banco"));
            costs.add(cost(MIP, "Seguro de morte e invalidez (MIP)", base.multiply(MIP_RATE), CostWhen.INSTALLMENT, true,
                    "Cerca de 0,025% do saldo ao mês; varia com a idade"));
            costs.add(cost("dfi", "Seguro do imóvel (DFI)", pct(price, "0.007"), CostWhen.INSTALLMENT, true,
                    "Cerca de 0,007% do valor do imóvel ao mês"));
            costs.add(cost("admin", "Taxa de administração", new BigDecimal("25"), CostWhen.INSTALLMENT, true, null));
            costs.add(cost("iptu", "IPTU", pct(price, "0.6"), CostWhen.YEARLY, false, "Estimativa: 0,6% do valor ao ano"));
            if (kind == FinancingKind.APARTMENT) {
                costs.add(cost("condo", "Condomínio", BigDecimal.ZERO, CostWhen.MONTHLY, false, "Informe o valor do prédio"));
            }
            return costs;
        }
        costs.add(cost("iof", "IOF", base.multiply(BigDecimal.valueOf(iof(months))), CostWhen.FINANCED, true,
                "0,38% + 0,0082% ao dia (até 3% ao ano)"));
        if (kind == FinancingKind.OTHER) {
            costs.add(cost("tac", "Tarifa de cadastro", new BigDecimal("500"), CostWhen.FINANCED, true, null));
            return costs;
        }
        boolean car = kind == FinancingKind.CAR;
        costs.add(cost("tac", "Tarifa de cadastro", new BigDecimal(car ? "800" : "600"), CostWhen.FINANCED, true,
                "Cobrada pelo banco no início"));
        costs.add(cost("lien", "Registro do contrato (gravame)", new BigDecimal(car ? "350" : "250"), CostWhen.FINANCED,
                true, "Taxa do Detran repassada pelo banco"));
        if (used) {
            costs.add(cost("appraisal", "Avaliação do veículo", new BigDecimal(car ? "400" : "250"), CostWhen.FINANCED,
                    true, null));
            costs.add(cost("transfer", "Transferência e vistoria", new BigDecimal(car ? "350" : "250"), CostWhen.UPFRONT,
                    false, "Detran e vistoria cautelar"));
        } else {
            costs.add(cost("plates", "Emplacamento e 1º licenciamento", new BigDecimal(car ? "450" : "300"),
                    CostWhen.UPFRONT, false, null));
        }
        costs.add(cost("ipva", "IPVA", pct(price, car ? "4" : "2"), CostWhen.YEARLY, false,
                "De 1% a 4% do valor, conforme o estado"));
        costs.add(cost("insurance", "Seguro do veículo", pct(price, car ? "5" : "8"), CostWhen.YEARLY, false,
                "Estimativa; varia com perfil e região"));
        costs.add(cost("licensing", "Licenciamento anual", new BigDecimal(car ? "160" : "120"), CostWhen.YEARLY, false,
                null));
        costs.add(cost("maintenance", "Revisões e manutenção",
                new BigDecimal(car ? (used ? "1500" : "1000") : (used ? "700" : "500")), CostWhen.YEARLY, false, null));
        return costs;
    }

    /** Troca o valor dos gastos que a pessoa editou (chave → valor; 0 tira o gasto da conta). */
    static List<CostItem> apply(List<CostItem> costs, Map<String, BigDecimal> overrides) {
        if (overrides == null || overrides.isEmpty()) return costs;
        return costs.stream().map(c -> overrides.containsKey(c.key())
                ? new CostItem(c.key(), c.name(), overrides.get(c.key()).setScale(2, RoundingMode.HALF_UP), c.when(),
                c.bank(), c.hint(), true)
                : c).toList();
    }

    /** IOF de pessoa física: 0,38% + 0,0082% ao dia sobre o prazo médio das parcelas, limitado a 365 dias. */
    private static double iof(int months) {
        double days = Math.min(365, (months + 1) / 2.0 * 30);
        return 0.0038 + days * 0.000082;
    }

    private static BigDecimal pct(BigDecimal value, String percent) {
        return value.multiply(new BigDecimal(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static CostItem cost(String key, String name, BigDecimal amount, CostWhen when, boolean bank, String hint) {
        return new CostItem(key, name, amount.setScale(2, RoundingMode.HALF_UP), when, bank, hint, false);
    }
}
