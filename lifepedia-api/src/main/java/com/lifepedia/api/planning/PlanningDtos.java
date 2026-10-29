package com.lifepedia.api.planning;

import com.lifepedia.api.user.UserDtos.UserSummary;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class PlanningDtos {

    private PlanningDtos() {
    }

    /**
     * @param installments 1 = à vista; parcelas começam no {@code targetMonth}
     * @param imageData    nova imagem (data URL ou base64 de JPEG/PNG); nula mantém a atual
     */
    public record PlanItemRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120, message = "Máximo de 120 caracteres.") String name,
            @NotNull(message = "Escolha o tipo.") PlanItem.Kind kind,
            @NotNull(message = "Informe o valor.")
            @DecimalMin(value = "0.01", message = "Informe um valor maior que zero.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            @NotNull(message = "Informe o prazo.") YearMonth targetMonth,
            @Min(value = 1, message = "Mínimo de 1 parcela.") @Max(value = 120, message = "Máximo de 120 parcelas.")
            Integer installments,
            @Size(max = 10000, message = "Máximo de 10000 caracteres.") String notes,
            Boolean active,
            @Size(max = 2048, message = "Link muito longo.") String url,
            @Size(max = 300) String linkTitle,
            @Size(max = 120) String linkSite,
            @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal linkPrice,
            @Size(max = 60) String referenceSite,
            @Size(max = 300) String referenceTitle,
            @Size(max = 2048) String referenceUrl,
            @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal referencePrice,
            @Size(max = 1_500_000, message = "A imagem deve ter até 1 MB.") String imageData,
            Boolean removeImage) {

        public int installmentsOrOne() {
            return installments == null ? 1 : installments;
        }

        public boolean removeImageOrFalse() {
            return Boolean.TRUE.equals(removeImage);
        }
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    /** Nova ordem de prioridade (itens fora da lista vão para o fim, na ordem atual). */
    public record OrderRequest(@NotEmpty @Size(max = 200) List<@NotNull UUID> itemIds) {
    }

    /** @param launch também lança a despesa no grupo (em parcelas, se o item for parcelado) */
    public record AcquireRequest(
            @NotNull(message = "Informe a data.") LocalDate date,
            @NotNull(message = "Informe o valor.")
            @DecimalMin(value = "0.01", message = "Informe um valor maior que zero.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            Boolean launch,
            UUID categoryId) {

        public boolean launchOrFalse() {
            return Boolean.TRUE.equals(launch);
        }
    }

    /** @param averageMonths 0 (só fixos), 3, 6 ou 12; @param manualBalance substitui o cálculo quando preenchido */
    public record SettingsRequest(
            @NotNull(message = "Informe a reserva.")
            @DecimalMin(value = "0", message = "A reserva não pode ser negativa.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal reserve,
            @NotNull Integer averageMonths,
            @DecimalMin(value = "-999999999999.99") @DecimalMax(value = "999999999999.99")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal manualBalance) {
    }

    /** @param itemIds nulo = itens ativos agora */
    public record ScenarioRequest(
            @NotBlank(message = "Informe o nome do cenário.") @Size(max = 60, message = "Máximo de 60 caracteres.") String name,
            @Size(max = 200) List<@NotNull UUID> itemIds) {
    }

    public record LinkPreviewRequest(@NotBlank(message = "Informe o link.") @Size(max = 2048) String url) {
    }

    public record ReferenceRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120, message = "Máximo de 120 caracteres.") String name) {
    }

    /** @param reference nulo = nada parecido */
    public record ReferenceLookup(Reference reference) {
    }

    /** @param canEdit quem cadastrou ou admin; @param referenceSite de onde vieram preço/foto quando a loja não liberou */
    public record PlanItemResponse(UUID id, String name, PlanItem.Kind kind, BigDecimal amount, YearMonth targetMonth,
                                   int installments, int position, boolean active, String notes, String url,
                                   String linkTitle, String linkSite, BigDecimal linkPrice, String referenceSite,
                                   String referenceTitle, String referenceUrl, BigDecimal referencePrice, String imageUrl,
                                   LocalDate acquiredOn, BigDecimal acquiredAmount, UserSummary createdBy,
                                   boolean canEdit) {
    }

    /**
     * @param considered   entra na simulação (ativo, ou no cenário vendo)
     * @param shortfall    quanto falta no prazo (0 = cabe); não considerado: quanto faltaria se entrasse
     * @param earliestMonth primeiro mês em que caberia; nulo = não cabe no horizonte
     * @param monthlySaving à vista: quanto guardar por mês até o prazo
     * @param installmentAmount parcelado: valor da parcela
     * @param overdue      prazo já passou (simulado no mês atual)
     */
    public record ItemResult(PlanItemResponse item, boolean considered, BigDecimal shortfall, YearMonth earliestMonth,
                             BigDecimal monthlySaving, BigDecimal installmentAmount, boolean overdue) {
    }

    /**
     * @param reserve      valor informado (guardado fora dos lançamentos)
     * @param invested     investimentos lançados no grupo até o mês atual
     * @param totalReserve reserve + invested, ponto de partida da simulação
     */
    public record Settings(BigDecimal reserve, int averageMonths, BigDecimal manualBalance, BigDecimal invested,
                           BigDecimal totalReserve) {
    }

    /**
     * Saldo previsto do primeiro mês e de onde vem.
     *
     * @param occasionalAverage média de (ganhos − despesas) ocasionais dos meses em {@code averageBasis}
     * @param averageBasis      meses usados na média: os completos da janela com lançamentos (ou só o atual)
     * @param extra             simulação "e se" (ganho extra − gasto extra), não salva
     */
    public record Base(BigDecimal fixedIncome, BigDecimal fixedExpense, BigDecimal occasionalAverage, int averageMonths,
                       List<YearMonth> averageBasis, BigDecimal manualBalance, BigDecimal extra, BigDecimal monthly) {
    }

    /**
     * @param shortfall     maior falta no período (0 = tudo cabe)
     * @param endBalance    saldo no mês do último pagamento (ou no fim do horizonte)
     */
    public record Outcome(int count, BigDecimal total, BigDecimal shortfall, BigDecimal lowestBalance,
                          YearMonth lowestMonth, BigDecimal endBalance, YearMonth endMonth) {
    }

    /** @param balance saldo acumulado no fim do mês, já descontadas as compras; @param due itens com prazo no mês */
    public record TimelinePoint(YearMonth month, BigDecimal base, BigDecimal planned, BigDecimal withoutPlan,
                                BigDecimal balance, List<UUID> due) {
    }

    /** @param scenarioId cenário simulado (nulo = itens ativos) */
    public record Simulation(YearMonth start, UUID scenarioId, Settings settings, Base base, Outcome outcome,
                             List<TimelinePoint> timeline, List<ItemResult> items, List<PlanItemResponse> acquired) {
    }

    public record ScenarioResponse(UUID id, String name, List<UUID> itemIds, UserSummary createdBy, boolean canEdit) {
    }

    /** @param scenarioId nulo = itens ativos agora */
    public record ScenarioComparison(UUID scenarioId, String name, Outcome outcome) {
    }

    /**
     * Dados lidos do link (tudo opcional).
     *
     * @param titleFromUrl nome tirado do próprio endereço (a página não liberou os dados)
     * @param imageData    foto já reduzida, como data URL JPEG, para mostrar e enviar de volta ao salvar
     * @param blocked      a loja não deixou ler a página
     * @param reference    produto parecido em outro site, quando faltou preço ou foto
     */
    public record LinkPreview(String url, String title, boolean titleFromUrl, BigDecimal price, String currency,
                              String site, String imageData, boolean blocked, Reference reference) {
    }

    /**
     * Produto parecido achado em outro site (preço e foto de referência).
     *
     * @param url    página do produto (ou da busca) nesse site
     * @param stores quantas lojas vendem (nulo = oferta de uma loja só)
     * @param store  loja do menor preço
     * @param imageData foto como data URL JPEG; nula se a página do link já tinha foto
     */
    public record Reference(String site, String title, String url, BigDecimal price, Integer stores, String store,
                            String imageData) {
    }
}
