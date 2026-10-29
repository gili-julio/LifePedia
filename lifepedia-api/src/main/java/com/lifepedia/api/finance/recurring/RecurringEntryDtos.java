package com.lifepedia.api.finance.recurring;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.group.ShareMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class RecurringEntryDtos {

    private RecurringEntryDtos() {
    }

    /**
     * @param startMonth {@code yyyy-MM}; padrão: mês atual
     * @param active     padrão: true
     * @param removeTransactionIds ocasionais que o fixo substitui; excluídos junto na criação
     * @param groupIds grupos para onde levar ao criar ({@code shareMode}, padrão SYNC); ignorados na edição
     */
    public record RecurringEntryRequest(
            @NotBlank(message = "Informe a descrição.") @Size(max = 120) String description,
            @NotNull(message = "Informe o tipo.") EntryType type,
            @NotNull(message = "Informe a categoria.") UUID categoryId,
            @NotNull(message = "Informe o valor.")
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            @Min(value = 1, message = "Dia inválido.") @Max(value = 31, message = "Dia inválido.") Integer dayOfMonth,
            YearMonth startMonth,
            YearMonth endMonth,
            Boolean active,
            @Size(max = 10000) String notes,
            @Size(max = 500) List<UUID> removeTransactionIds,
            @Size(max = 20) List<UUID> groupIds,
            ShareMode shareMode) {
    }

    public record RecurringEntryResponse(
            UUID id,
            String description,
            EntryType type,
            UUID categoryId,
            BigDecimal amount,
            Integer dayOfMonth,
            YearMonth startMonth,
            YearMonth endMonth,
            boolean active,
            String notes,
            List<AdjustmentResponse> adjustments) {

        public static RecurringEntryResponse from(RecurringEntry e, List<RecurringAdjustment> adjustments) {
            return new RecurringEntryResponse(e.getId(), e.getDescription(), e.getType(), e.getCategoryId(),
                    e.getAmount(), e.getDayOfMonth(), e.getStartMonth(), e.getEndMonth(), e.isActive(), e.getNotes(),
                    adjustments.stream()
                            .sorted(Comparator.comparing(RecurringAdjustment::getMonth).reversed())
                            .map(AdjustmentResponse::from)
                            .toList());
        }
    }

    public record AdjustmentRequest(
            @NotNull(message = "Informe o valor.")
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount) {
    }

    /** Valor real do fixo no mês. */
    public record AdjustmentResponse(YearMonth month, BigDecimal amount) {

        public static AdjustmentResponse from(RecurringAdjustment a) {
            return new AdjustmentResponse(a.getMonth(), a.getAmount());
        }
    }
}
