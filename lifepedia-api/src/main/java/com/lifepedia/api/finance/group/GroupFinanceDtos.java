package com.lifepedia.api.finance.group;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.summary.SummaryDtos.Totals;
import com.lifepedia.api.user.UserDtos.UserSummary;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class GroupFinanceDtos {

    private GroupFinanceDtos() {
    }

    /** Itens pessoais a levar para o grupo. */
    public record ShareRequest(
            @Size(max = 500) List<@NotNull UUID> transactionIds,
            @Size(max = 500) List<@NotNull UUID> recurringIds,
            @NotNull(message = "Escolha sincronizar ou copiar.") ShareMode mode) {

        public List<UUID> transactionIdsOrEmpty() {
            return transactionIds == null ? List.of() : transactionIds;
        }

        public List<UUID> recurringIdsOrEmpty() {
            return recurringIds == null ? List.of() : recurringIds;
        }
    }

    /** @param skipped já sincronizados antes */
    public record ShareResult(int added, int skipped) {
    }

    /** Itens pessoais do usuário que já estão no grupo. */
    public record Origins(List<Origin> transactions, List<Origin> recurring) {
    }

    public record Origin(UUID originId, boolean synced) {
    }

    /** Categoria de quem levou o item. */
    public record CategoryRef(UUID id, String name, String color) {
    }

    /** @param mine levado pelo usuário atual; @param synced espelho do item pessoal (edição só no pessoal) */
    public record GroupTransactionResponse(UUID id, String description, EntryType type, BigDecimal amount,
                                           LocalDate date, YearMonth month, String notes, CategoryRef category,
                                           UserSummary member, boolean synced, boolean mine) {
    }

    public record GroupRecurringResponse(UUID id, String description, EntryType type, BigDecimal amount,
                                         Integer dayOfMonth, YearMonth startMonth, YearMonth endMonth, boolean active,
                                         String notes, CategoryRef category, UserSummary member, boolean synced,
                                         boolean mine) {
    }

    /** Categorias somadas pelo nome (cada membro tem as suas); {@code categoryId} é a chave do nome. */
    public record GroupCategoryBreakdown(String categoryId, String name, String color,
                                         BigDecimal fixed, BigDecimal occasional, BigDecimal total) {
    }

    /** @param expense sem os investimentos; {@code balance} desconta os dois */
    public record MemberTotals(UserSummary member, BigDecimal income, BigDecimal expense, BigDecimal invested,
                               BigDecimal balance) {
    }

    /**
     * Reserva do grupo até o mês.
     *
     * @param manual   valor informado no planejamento (guardado fora dos lançamentos)
     * @param invested investimentos lançados no grupo até o mês
     */
    public record Reserve(BigDecimal manual, BigDecimal invested, BigDecimal total) {

        public static Reserve of(BigDecimal manual, BigDecimal invested) {
            return new Reserve(manual, invested, manual.add(invested));
        }
    }

    /** Mesmo formato do resumo pessoal, com as categorias por nome e o total por membro. */
    public record GroupSummary(
            YearMonth month,
            Totals fixed,
            Totals occasional,
            Totals total,
            BigDecimal fixedCommitmentPercent,
            BigDecimal investedPercent,
            List<GroupCategoryBreakdown> expensesByCategory,
            List<GroupCategoryBreakdown> incomesByCategory,
            List<MemberTotals> members,
            Reserve reserve) {
    }
}
