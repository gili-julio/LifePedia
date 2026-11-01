package com.lifepedia.api.finance.bank;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class BankDtos {

    private BankDtos() {
    }

    /** Define o banco de vários itens de uma vez; {@code bank} nulo tira o banco. */
    public record AssignRequest(
            @Size(max = 500, message = "Escolha no máximo 500 lançamentos por vez.") List<@NotNull UUID> transactionIds,
            @Size(max = 500, message = "Escolha no máximo 500 fixos por vez.") List<@NotNull UUID> recurringIds,
            @Valid BankRef bank) {

        public List<UUID> transactionIdsOrEmpty() {
            return transactionIds == null ? List.of() : transactionIds;
        }

        public List<UUID> recurringIdsOrEmpty() {
            return recurringIds == null ? List.of() : recurringIds;
        }
    }

    public record AssignResult(int updated) {
    }

    /** Último banco usado, para abrir o formulário preenchido; nulo se nunca usou. */
    public record LastBank(BankRef bank) {
    }

    public record BankCategory(String name, String color, BigDecimal total) {
    }

    /**
     * Um banco no mês. Despesas sem os investimentos, como no resumo.
     *
     * @param key        para filtrar ({@link BankRef#key()}); {@code NONE} = sem banco
     * @param code       nulo = sem banco
     * @param fixed      parte das despesas em fixos (o resto em {@code occasional})
     * @param account    parte das despesas na conta (o resto em {@code card}); zero sem banco
     * @param categories despesas do mês por categoria, as maiores primeiro
     * @param history    despesas de cada mês de {@link BankOverview#months()}
     */
    public record BankLine(String key, String code, String name, BigDecimal income, BigDecimal expense,
                           BigDecimal invested, BigDecimal fixed, BigDecimal occasional, BigDecimal account,
                           BigDecimal card, List<BankCategory> categories, List<BigDecimal> history) {
    }

    /** @param expense despesas do mês em todos os bancos */
    public record BankOverview(YearMonth month, List<YearMonth> months, BigDecimal expense, List<BankLine> banks) {
    }
}
