package com.lifepedia.api.finance.importing;

import com.lifepedia.api.finance.bank.BankRef;
import com.lifepedia.api.finance.importing.ImportDraft.Origin;
import com.lifepedia.api.finance.importing.ImportDraft.Status;
import com.lifepedia.api.finance.transaction.TransactionDtos.TransactionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
            List<@Valid @NotNull FixedAdjustment> fixedAdjustments,
            @Size(max = 200, message = "Máximo de 200 fixos por vez.")
            List<@Valid @NotNull FixedBank> fixedBanks,
            UUID draftId) {

        public List<TransactionRequest> transactionsOrEmpty() {
            return transactions == null ? List.of() : transactions;
        }

        public List<FixedAdjustment> fixedAdjustmentsOrEmpty() {
            return fixedAdjustments == null ? List.of() : fixedAdjustments;
        }

        public List<FixedBank> fixedBanksOrEmpty() {
            return fixedBanks == null ? List.of() : fixedBanks;
        }
    }

    /** Banco que veio no documento para um fixo sem banco. */
    public record FixedBank(
            @NotNull(message = "Informe o fixo.") UUID recurringEntryId,
            @NotNull(message = "Informe o banco.") @Valid BankRef bank) {
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

    /** O que a tela precisa saber da leitura pelo site, com os limites para mostrar. */
    public record AssistantConfig(boolean enabled, int maxFiles, long maxTotalBytes, int maxPages, ImportQuota quota) {
    }

    public record PromptResponse(String prompt) {
    }

    public record DraftResponse(UUID id, Origin origin, Status status, int fileCount, int itemCount, String error,
                                OffsetDateTime createdAt) {

        static DraftResponse from(ImportDraft draft) {
            return new DraftResponse(draft.getId(), draft.getOrigin(), draft.getStatus(), draft.getFileCount(),
                    draft.getItemCount(), draft.getError(), draft.getCreatedAt());
        }
    }

    /** @param rows estado da prévia; nulo = abrir pelo {@code result}, a resposta da IA */
    public record DraftContent(String result, String rows) {
    }

    /** @param rows prévia em JSON: só o front lê */
    public record DraftRequest(
            @NotBlank(message = "Revisão vazia.")
            @Size(max = 2_000_000, message = "Revisão grande demais.") String rows,
            @NotNull(message = "Informe a quantidade de itens.")
            @Min(value = 1, message = "Revisão vazia.")
            @Max(value = 500, message = "Importe no máximo 500 lançamentos por vez.") Integer itemCount) {
    }
}
