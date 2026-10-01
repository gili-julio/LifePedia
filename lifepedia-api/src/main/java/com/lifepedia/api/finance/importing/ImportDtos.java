package com.lifepedia.api.finance.importing;

import com.lifepedia.api.finance.transaction.TransactionDtos.TransactionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class ImportDtos {

    private ImportDtos() {
    }

    /** Resultado de um extrato/fatura: ocasionais novos + valor real de fixos no mês. */
    public record ImportRequest(
            @Size(max = 500, message = "Importe no máximo 500 lançamentos por vez.")
            List<@Valid @NotNull TransactionRequest> transactions,
            @Size(max = 200, message = "Máximo de 200 ajustes por vez.")
            List<@Valid @NotNull FixedAdjustment> fixedAdjustments) {

        public List<TransactionRequest> transactionsOrEmpty() {
            return transactions == null ? List.of() : transactions;
        }

        public List<FixedAdjustment> fixedAdjustmentsOrEmpty() {
            return fixedAdjustments == null ? List.of() : fixedAdjustments;
        }
    }

    public record FixedAdjustment(
            @NotNull(message = "Informe o fixo.") UUID recurringEntryId,
            @NotNull(message = "Informe o mês.") YearMonth month,
            @NotNull(message = "Informe o valor.")
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount) {
    }

    public record ImportResponse(int transactions, int fixedAdjustments) {
    }
}
