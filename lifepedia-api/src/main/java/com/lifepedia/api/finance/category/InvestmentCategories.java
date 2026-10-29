package com.lifepedia.api.finance.category;

import com.lifepedia.api.finance.EntryType;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Despesa em categoria "Investir"/"Investimento(s)" (sem diferenciar acento e maiúscula) é dinheiro guardado: fica fora
 * das despesas, desconta do saldo e soma na reserva dos grupos.
 */
public final class InvestmentCategories {

    private static final Set<String> NAMES = Set.of("investir", "investimento", "investimentos");

    private InvestmentCategories() {
    }

    public static boolean isInvestment(Category category) {
        return category != null && category.getType() == EntryType.EXPENSE && NAMES.contains(normalize(category.getName()));
    }

    public static Set<UUID> ids(Collection<Category> categories) {
        return categories.stream().filter(InvestmentCategories::isInvestment).map(Category::getId).collect(Collectors.toSet());
    }

    private static String normalize(String name) {
        return Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
