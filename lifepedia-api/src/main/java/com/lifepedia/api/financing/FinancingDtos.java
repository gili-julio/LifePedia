package com.lifepedia.api.financing;

import com.lifepedia.api.financing.FinancingCost.Frequency;
import com.lifepedia.api.financing.FinancingMath.AmortizationSystem;
import com.lifepedia.api.financing.FinancingMath.ExtraMode;
import com.lifepedia.api.financing.FinancingMath.Row;
import com.lifepedia.api.user.UserDtos.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FinancingDtos {

    private FinancingDtos() {
    }

    /** Taxa usada: quartis das taxas dos bancos (LOW, MEDIAN, HIGH) ou a informada pela pessoa (CUSTOM). */
    public enum Scenario { LOW, MEDIAN, HIGH, CUSTOM }

    /**
     * Quando o gasto é pago: à vista na compra, somado ao valor financiado, dentro de cada parcela, todo ano ou todo
     * mês (gastos de ter o bem, fora da parcela).
     */
    public enum CostWhen { UPFRONT, FINANCED, INSTALLMENT, YEARLY, MONTHLY }

    /**
     * Sem taxa: a simulação usa as taxas médias do Banco Central.
     *
     * @param startMonth mês da entrada (padrão: atual); a 1ª parcela vence no mês seguinte
     * @param customRate % ao mês de uma proposta do banco (opcional)
     * @param costs      valor editado de cada gasto padrão, pela chave
     * @param scenario   e @param system escolhem a tabela de parcelas devolvida
     */
    public record SimulationRequest(
            @NotNull(message = "Escolha o tipo do bem.") FinancingKind kind,
            @NotNull(message = "Diga se o bem é novo ou usado.") ItemCondition condition,
            @NotNull(message = "Informe o preço.")
            @DecimalMin(value = "1000", message = "O preço precisa ser de pelo menos R$ 1.000.")
            @Digits(integer = 12, fraction = 2, message = "Preço inválido.") BigDecimal price,
            @NotNull(message = "Informe a entrada (pode ser zero).")
            @DecimalMin(value = "0", message = "Entrada inválida.")
            @Digits(integer = 12, fraction = 2, message = "Entrada inválida.") BigDecimal downPayment,
            @DecimalMin(value = "0", message = "FGTS inválido.")
            @Digits(integer = 12, fraction = 2, message = "FGTS inválido.") BigDecimal fgts,
            YearMonth startMonth,
            @Min(value = 1, message = "Prazo inválido.") @Max(value = 420, message = "Prazo máximo: 420 meses.") Integer months,
            @DecimalMin(value = "0", message = "Renda inválida.")
            @Digits(integer = 12, fraction = 2, message = "Renda inválida.") BigDecimal income,
            @DecimalMin(value = "0", message = "Taxa inválida.") @DecimalMax(value = "20", message = "Taxa alta demais.")
            @Digits(integer = 2, fraction = 4, message = "Taxa inválida.") BigDecimal customRate,
            @Size(max = 30) Map<@NotBlank @Size(max = 30) String,
                    @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal> costs,
            Scenario scenario,
            AmortizationSystem system) {
    }

    /** @param period início da divulgação do Banco Central (nulo = taxa padrão, sem dados) */
    public record RateSource(Modality modality, String label, LocalDate period, int banks, boolean fallback) {
    }

    /** @param monthly variação média prevista ao mês (%) */
    public record CorrectionInfo(CorrectionIndex index, BigDecimal monthly) {
    }

    /** @param cetMonthly e @param cetYearly custo efetivo total (%) */
    public record SystemResult(BigDecimal firstPayment, BigDecimal lastPayment, BigDecimal totalPaid,
                               BigDecimal totalInterest, BigDecimal cetMonthly, BigDecimal cetYearly) {
    }

    public record ScenarioResult(Scenario scenario, BigDecimal monthlyRate, BigDecimal yearlyRate, SystemResult sac,
                                 SystemResult price) {
    }

    /** Mesmo valor e taxa, outro prazo. */
    public record TermRow(int months, BigDecimal priceFirst, BigDecimal priceInterest, BigDecimal sacFirst,
                          BigDecimal sacLast, BigDecimal sacInterest) {
    }

    /** @param edited valor mudado pela pessoa (fora do padrão) */
    public record CostItem(String key, String name, BigDecimal amount, CostWhen when, boolean bank, String hint,
                           boolean edited) {
    }

    /**
     * @param upfront        entrada + FGTS + gastos à vista
     * @param upfrontCosts   só os gastos à vista
     * @param financedCosts  gastos somados ao financiamento
     * @param installmentFee seguros e tarifas da 1ª parcela
     * @param yearly         gastos anuais de ter o bem
     * @param monthly        gastos mensais de ter o bem
     */
    public record CostTotals(BigDecimal upfront, BigDecimal upfrontCosts, BigDecimal financedCosts,
                             BigDecimal installmentFee, BigDecimal yearly, BigDecimal monthly) {
    }

    /** @param percent 1ª parcela sobre a renda; @param maxPrice preço máximo com parcela até {@code limit}% da renda */
    public record IncomeCheck(BigDecimal income, BigDecimal payment, BigDecimal percent, BigDecimal limit,
                              BigDecimal maxPayment, BigDecimal maxPrice) {
    }

    /** @param payment 1ª parcela no sistema e prazo escolhidos */
    public record BankOption(int position, String bank, BigDecimal monthlyRate, BigDecimal yearlyRate,
                             BigDecimal payment) {
    }

    /**
     * @param base     preço menos entrada e FGTS
     * @param financed base + gastos financiados (valor do contrato)
     * @param schedule parcelas do cenário e sistema escolhidos
     */
    public record SimulationResponse(RateSource rates, BigDecimal base, BigDecimal financed, int months,
                                     YearMonth firstMonth, CorrectionInfo correction, List<ScenarioResult> scenarios,
                                     List<TermRow> terms, List<CostItem> costs, CostTotals costTotals,
                                     IncomeCheck income, List<BankOption> banks, Scenario scenario,
                                     AmortizationSystem system, List<Row> schedule, List<String> warnings) {
    }

    /** Taxas do Banco Central para preencher um contrato (% ao mês). */
    public record RateSuggestion(RateSource source, BigDecimal low, BigDecimal median, BigDecimal high,
                                 CorrectionIndex index) {
    }

    public record SimulationSaveRequest(
            @NotBlank(message = "Dê um nome à simulação.") @Size(max = 120, message = "Nome muito longo.") String name,
            @NotNull @Valid SimulationRequest input) {
    }

    public record SavedSimulation(UUID id, String name, SimulationRequest input, UserSummary createdBy,
                                  OffsetDateTime createdAt, boolean canDelete) {
    }

    public record ShareRequest(@NotNull UUID userId,
                               @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal share) {
    }

    /**
     * Contrato. Taxas em % ao mês.
     *
     * @param insuranceRate seguro sobre o saldo (MIP), % ao mês
     * @param monthlyFee    valor fixo de cada parcela (seguro do bem, tarifa)
     * @param reminderDays  avisar quantos dias antes de cada vencimento (nulo = sem aviso)
     * @param shares        parte de cada membro (só grupo; soma 100); nulo mantém (ou divide igual ao criar)
     */
    public record FinancingRequest(
            @NotBlank(message = "Dê um nome ao financiamento.") @Size(max = 120, message = "Nome muito longo.") String name,
            @NotNull(message = "Escolha o tipo do bem.") FinancingKind kind,
            @NotNull(message = "Diga se o bem é novo ou usado.") ItemCondition condition,
            @DecimalMin(value = "0", message = "Preço inválido.") @Digits(integer = 12, fraction = 2) BigDecimal price,
            @DecimalMin(value = "0", message = "Entrada inválida.") @Digits(integer = 12, fraction = 2) BigDecimal downPayment,
            @DecimalMin(value = "0", message = "FGTS inválido.") @Digits(integer = 12, fraction = 2) BigDecimal fgts,
            @NotNull(message = "Informe o valor financiado.")
            @DecimalMin(value = "1", message = "Valor financiado inválido.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal financed,
            @NotNull(message = "Informe a taxa de juros.")
            @DecimalMin(value = "0", message = "Taxa inválida.") @DecimalMax(value = "20", message = "Taxa alta demais.")
            @Digits(integer = 2, fraction = 4, message = "Taxa inválida.") BigDecimal monthlyRate,
            @NotNull(message = "Escolha SAC ou Price.") AmortizationSystem system,
            @NotNull(message = "Informe o prazo.")
            @Min(value = 1, message = "Prazo inválido.") @Max(value = 420, message = "Prazo máximo: 420 meses.") Integer months,
            @NotNull(message = "Informe o mês da 1ª parcela.") YearMonth firstMonth,
            @NotNull(message = "Informe o dia do vencimento.")
            @Min(value = 1, message = "Dia inválido.") @Max(value = 31, message = "Dia inválido.") Integer dueDay,
            @DecimalMin(value = "0", message = "Seguro inválido.") @DecimalMax(value = "5", message = "Seguro inválido.")
            @Digits(integer = 1, fraction = 5, message = "Seguro inválido.") BigDecimal insuranceRate,
            @DecimalMin(value = "0", message = "Tarifa inválida.") @Digits(integer = 12, fraction = 2) BigDecimal monthlyFee,
            CorrectionIndex index,
            @Size(max = 160, message = "Nome do banco muito longo.") String bank,
            @Min(value = 0, message = "Aviso inválido.") @Max(value = 15, message = "Avise até 15 dias antes.") Integer reminderDays,
            @Size(max = 2000, message = "Observação muito longa.") String notes,
            @Size(max = 50) List<@NotNull @Valid ShareRequest> shares) {
    }

    public record NextPayment(int number, YearMonth month, LocalDate date, BigDecimal amount) {
    }

    /**
     * Situação hoje: parcela paga = vencimento já passou.
     *
     * @param balance       saldo devedor depois da última parcela paga
     * @param percent       % do valor financiado já amortizado
     * @param payoff        valor estimado para quitar hoje
     * @param payoffSavings parcelas que faltam menos o valor para quitar (juros e seguros que deixam de ser pagos)
     */
    public record Progress(int paid, int remaining, int totalMonths, YearMonth lastMonth, BigDecimal balance,
                           BigDecimal percent, BigDecimal interestPaid, BigDecimal interestRemaining,
                           NextPayment next, BigDecimal payoff, BigDecimal payoffSavings) {
    }

    /** @param nextAmount parte da pessoa na próxima parcela; @param recurringEntryId fixo pessoal com a parte (só para ela) */
    public record Member(UserSummary user, BigDecimal share, BigDecimal nextAmount, UUID recurringEntryId, boolean fixed,
                         boolean me) {
    }

    public record ExtraPayment(UUID id, YearMonth month, BigDecimal amount, ExtraMode mode) {
    }

    public record ExtraRequest(
            @NotNull(message = "Informe o mês.") YearMonth month,
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "1", message = "Valor inválido.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            @NotNull(message = "Escolha reduzir o prazo ou a parcela.") ExtraMode mode) {
    }

    /** Comparação sem e com a amortização extra. */
    public record ExtraPreview(int monthsBefore, int monthsAfter, YearMonth lastMonthBefore, YearMonth lastMonthAfter,
                               BigDecimal nextPaymentBefore, BigDecimal nextPaymentAfter, BigDecimal interestSaved,
                               BigDecimal totalSaved) {
    }

    public record CostRequest(
            @NotBlank(message = "Dê um nome ao gasto.") @Size(max = 80, message = "Nome muito longo.") String name,
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "Valor inválido.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            @NotNull(message = "Escolha mensal ou anual.") Frequency frequency,
            @Min(value = 1, message = "Mês inválido.") @Max(value = 12, message = "Mês inválido.") Integer dueMonth,
            @NotNull(message = "Informe o dia.") @Min(value = 1, message = "Dia inválido.")
            @Max(value = 31, message = "Dia inválido.") Integer dueDay) {
    }

    public record Cost(UUID id, String name, BigDecimal amount, Frequency frequency, Integer dueMonth, int dueDay,
                       LocalDate nextDate) {
    }

    /** @param shareToGroup no grupo: leva o fixo sincronizado para o grupo */
    public record MyFixedRequest(@NotNull(message = "Diga se liga ou desliga.") Boolean enabled, UUID categoryId,
                                 Boolean shareToGroup) {
    }

    public record FinancingResponse(UUID id, UUID groupId, String name, FinancingKind kind, ItemCondition condition,
                                    BigDecimal price, BigDecimal downPayment, BigDecimal fgts, BigDecimal financed,
                                    BigDecimal monthlyRate, BigDecimal yearlyRate, AmortizationSystem system,
                                    int months, YearMonth firstMonth, int dueDay, BigDecimal insuranceRate,
                                    BigDecimal monthlyFee, CorrectionIndex index, BigDecimal indexProjection,
                                    String bank, Integer reminderDays, String notes, Progress progress,
                                    List<Member> members, List<ExtraPayment> extras, List<Cost> costs,
                                    List<Row> schedule, UserSummary createdBy, boolean canDelete) {
    }

    /** @param myShare parte da pessoa (%); @param groupName nulo = pessoal */
    public record FinancingSummary(UUID id, UUID groupId, String groupName, String name, FinancingKind kind,
                                   BigDecimal financed, Progress progress, BigDecimal myShare) {
    }

    /** Registro no arquivo da conta (não é importado). */
    public record FinancingData(String name, FinancingKind kind, ItemCondition condition, BigDecimal price,
                                BigDecimal downPayment, BigDecimal fgts, BigDecimal financed, BigDecimal monthlyRate,
                                AmortizationSystem system, int months, YearMonth firstMonth, int dueDay,
                                BigDecimal insuranceRate, BigDecimal monthlyFee, CorrectionIndex index, String bank,
                                String notes, List<ExtraPayment> extras, List<Cost> costs) {
    }

    public record SimulationData(String name, SimulationRequest input, OffsetDateTime createdAt) {
    }
}
