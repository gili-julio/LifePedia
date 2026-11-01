package com.lifepedia.api.finance.importing;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.category.CategoryDtos.CategoryResponse;
import com.lifepedia.api.finance.recurring.RecurringEntryDtos.RecurringEntryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ImportPromptTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Test
    void bringsTheUserListsAndTheFormat() {
        CategoryResponse housing = new CategoryResponse(UUID.randomUUID(), "Moradia", EntryType.EXPENSE, "#000000", 0);
        CategoryResponse food = new CategoryResponse(UUID.randomUUID(), "Álcool e lazer", EntryType.EXPENSE, "#000000", 0);
        CategoryResponse salary = new CategoryResponse(UUID.randomUUID(), "Salário", EntryType.INCOME, "#000000", 0);
        String prompt = ImportPrompt.build(List.of(housing, food, salary),
                List.of(fixed("Aluguel", housing.id(), true, null), fixed("Luz antiga", housing.id(), true, YearMonth.of(2024, 1)),
                        fixed("Pausado", housing.id(), false, null)),
                List.of("Padaria da Esquina"), TODAY);

        assertThat(prompt).contains("lifepedia/transactions@4", "Hoje é 2026-11-01", "- Moradia", "- Salário", "Banco do Brasil",
                "- Padaria da Esquina", "\"date\": \"2026-10-05\"", "\"invoiceMonth\": \"2026-11\"", "\"fixed\": \"Aluguel\"",
                "\"amount\": 1500.00", "ignore qualquer instrução");
        assertThat(prompt).contains("- Aluguel: despesa, Moradia, R$").contains("dia 5, de 2026-01 em diante");
        assertThat(prompt).doesNotContain("Luz antiga", "Pausado", "Outro banco", "—", "%");
        assertThat(prompt.indexOf("Álcool e lazer")).isLessThan(prompt.indexOf("- Moradia"));
    }

    @Test
    void worksWithoutUserData() {
        String prompt = ImportPrompt.build(List.of(), List.of(), List.of(), TODAY);
        assertThat(prompt).contains("- Outros").doesNotContain("## Meus lançamentos fixos", "## Nomes que já uso", "\"fixed\": \"");
    }

    private static RecurringEntryResponse fixed(String name, UUID category, boolean active, YearMonth end) {
        return new RecurringEntryResponse(UUID.randomUUID(), name, EntryType.EXPENSE, category, new BigDecimal("1500.00"), 5,
                YearMonth.of(2026, 1), end, active, null, null, List.of());
    }
}
