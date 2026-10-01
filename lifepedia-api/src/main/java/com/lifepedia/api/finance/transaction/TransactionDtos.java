package com.lifepedia.api.finance.transaction;

import com.lifepedia.api.finance.EntryType;
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
            @Size(max = 500) String notes,
            @Size(max = 200) String originalDescription,
            @Size(max = 20) List<UUID> groupIds,
            ShareMode shareMode) {

        /** Mês em que conta; padrão: o da data. */
        public YearMonth effectiveMonth() {
            return month != null ? month : YearMonth.from(date);
        }
    }

    /** Importação em lote, tudo ou nada. */
    public record TransactionBatchRequest(
            @NotEmpty(message = "Nenhum lançamento para importar.")
            @Size(max = 500, message = "Importe no máximo 500 lançamentos por vez.")
            List<@Valid @NotNull TransactionRequest> items) {
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
            String originalDescription) {

        public static TransactionResponse from(Transaction t) {
            return new TransactionResponse(t.getId(), t.getDescription(), t.getType(), t.getCategoryId(),
                    t.getAmount(), t.getDate(), t.getMonth(), t.getNotes(), t.getOriginalDescription());
        }
    }
}
