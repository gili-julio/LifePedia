package com.lifepedia.api.finance.transaction;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.bank.BankRef;
import com.lifepedia.api.finance.group.ShareMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    /** {@code groupIds}/{@code shareMode}: grupos para onde levar ao criar (padrão SYNC); ignorados na edição. */
    public record TransactionRequest(
            @NotBlank(message = "Informe a descrição.") @Size(max = 120) String description,
            @NotNull(message = "Informe o tipo.") EntryType type,
            @NotNull(message = "Informe a categoria.") UUID categoryId,
            @NotNull(message = "Informe o valor.")
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            @NotNull(message = "Informe a data.") LocalDate date,
            YearMonth month,
            @Size(max = 10000) String notes,
            @Size(max = 200) String originalDescription,
            @Valid BankRef bank,
            @Size(max = 20) List<UUID> groupIds,
            ShareMode shareMode) {

        /** Mês em que conta; padrão: o da data. */
        public YearMonth effectiveMonth() {
            return month != null ? month : YearMonth.from(date);
        }
    }

    /** Lançamento já existente que veio no documento importado: fica conferido; valor e mês do documento valem, se vierem. */
    public record CheckRequest(
            @NotNull(message = "Informe o lançamento.") UUID transactionId,
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            YearMonth month) {
    }

    /** Importação em lote, tudo ou nada. */
    public record TransactionBatchRequest(
            @NotEmpty(message = "Nenhum lançamento para importar.")
            @Size(max = 500, message = "Importe no máximo 500 lançamentos por vez.")
            List<@Valid @NotNull TransactionRequest> items) {
    }

    /** Muda a categoria e/ou o mês de vários lançamentos, tudo ou nada. */
    public record BatchUpdateRequest(
            @NotEmpty(message = "Escolha os lançamentos.")
            @Size(max = 500, message = "Escolha no máximo 500 lançamentos.") List<@NotNull UUID> ids,
            UUID categoryId,
            YearMonth month) {
    }

    public record BatchDeleteRequest(
            @NotEmpty(message = "Escolha os lançamentos.")
            @Size(max = 500, message = "Escolha no máximo 500 lançamentos.") List<@NotNull UUID> ids) {
    }

    public record BatchResult(int count) {
    }

    /** Página da busca na tela de lançamentos, com a soma de tudo o que achou (não só da página). */
    public record SearchPage(List<TransactionResponse> items, int page, int size, long totalItems, int totalPages,
                             BigDecimal income, BigDecimal expense) {
    }

    public record TransactionResponse(
            UUID id,
            String description,
            EntryType type,
            UUID categoryId,
            BigDecimal amount,
            LocalDate date,
            YearMonth month,
            String notes,
            String originalDescription,
            BankRef bank,
            boolean checked) {

        public static TransactionResponse from(Transaction t) {
            return new TransactionResponse(t.getId(), t.getDescription(), t.getType(), t.getCategoryId(),
                    t.getAmount(), t.getDate(), t.getMonth(), t.getNotes(), t.getOriginalDescription(), t.getBank(), t.isChecked());
        }
    }
}
