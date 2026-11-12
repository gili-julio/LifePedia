package com.lifepedia.api.finance.simulation;

import com.lifepedia.api.finance.EntryType;
import jakarta.validation.Valid;
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
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class SimulatorDtos {

    private SimulatorDtos() {
    }

    /** Onde o dinheiro investido rende: % do CDI ou da Selic, IPCA + taxa, taxa fixa ao ano ou poupança. */
    public enum Yield { CDI, SELIC, IPCA, FIXED, SAVINGS }

    /**
     * Pedido da simulação.
     *
     * @param useFixed      soma os fixos cadastrados de cada mês
     * @param averageMonths média dos ocasionais dos últimos 0 (sem), 3, 6 ou 12 meses completos
     * @param changes       ganhos e gastos livres
     */
    public record Input(
            @NotNull(message = "Informe o prazo.")
            @Min(value = 1, message = "Prazo mínimo: 1 mês.")
            @Max(value = 600, message = "Prazo máximo: 600 meses (50 anos).") Integer months,
            @NotNull(message = "Informe o saldo de hoje.")
            @DecimalMin(value = "-999999999", message = "Saldo inválido.") @DecimalMax(value = "999999999", message = "Saldo inválido.")
            @Digits(integer = 9, fraction = 2, message = "Saldo inválido.") BigDecimal startBalance,
            @NotNull Boolean useFixed,
            @NotNull(message = "Escolha a média dos ocasionais.") Integer averageMonths,
            @Size(max = 30, message = "Até 30 ganhos e gastos.") List<@NotNull @Valid Change> changes,
            @Valid Investment investment) {
    }

    /** @param end último mês (nulo = até o fim); @param once só no mês {@code start} */
    public record Change(
            @NotBlank(message = "Dê um nome.") @Size(max = 80, message = "Nome muito longo.") String name,
            @NotNull(message = "Escolha ganho ou gasto.") EntryType type,
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "Valor inválido.")
            @Digits(integer = 9, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            @NotNull(message = "Informe o mês.") YearMonth start,
            YearMonth end,
            @NotNull Boolean once) {
    }

    /**
     * @param initial     quanto já está investido (não sai do saldo)
     * @param monthly     aporte de cada mês
     * @param rate        % do CDI/Selic, taxa a.a. somada ao IPCA ou taxa fixa a.a. (poupança ignora)
     * @param tax         desconta o IR da tabela regressiva (poupança é isenta)
     * @param fromBalance o aporte sai do saldo do mês
     */
    public record Investment(
            @NotNull(message = "Informe o valor inicial.") @DecimalMin(value = "0", message = "Valor inválido.")
            @Digits(integer = 9, fraction = 2, message = "Valor inválido.") BigDecimal initial,
            @NotNull(message = "Informe o aporte.") @DecimalMin(value = "0", message = "Aporte inválido.")
            @Digits(integer = 9, fraction = 2, message = "Aporte inválido.") BigDecimal monthly,
            @NotNull(message = "Escolha onde rende.") Yield source,
            @DecimalMin(value = "0", message = "Taxa inválida.") @DecimalMax(value = "300", message = "Taxa inválida.")
            @Digits(integer = 3, fraction = 2, message = "Taxa inválida.") BigDecimal rate,
            @NotNull Boolean tax,
            @NotNull Boolean fromBalance) {
    }

    public record SaveRequest(
            @NotBlank(message = "Dê um nome.") @Size(max = 120, message = "Nome muito longo.") String name,
            @NotNull @Valid Input input) {
    }

    public record CompareRequest(
            @NotEmpty(message = "Escolha as simulações.") @Size(min = 2, max = 3, message = "Compare 2 ou 3 simulações.")
            List<@NotNull UUID> ids) {
    }

    public record Saved(UUID id, String name, Input input, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    /** Para o arquivo da conta. */
    public record SavedData(String name, Input input, OffsetDateTime createdAt) {
    }

    /**
     * Base do mês atual (sempre nominal).
     *
     * @param averageBasis meses usados na média dos ocasionais
     * @param monthly      saldo previsto do 1º mês sem mudanças
     */
    public record Base(BigDecimal fixedIncome, BigDecimal fixedExpense, BigDecimal occasionalAverage,
                       List<YearMonth> averageBasis, BigDecimal monthly) {
    }

    /**
     * Taxas ao ano (%), média dos últimos 12 meses do Banco Central.
     *
     * @param fallback algum índice sem dados (usou o padrão)
     */
    public record Rates(BigDecimal cdi, BigDecimal selic, BigDecimal ipca, boolean fallback) {
    }

    /**
     * Um mês. Valores acumulados no fim do mês.
     *
     * @param current     saldo sem mudanças (só a base)
     * @param simulated   saldo simulado + investimento líquido
     * @param cash        saldo simulado sem o investimento
     * @param contributed total aportado (com o inicial)
     * @param interest    rendimento bruto acumulado
     * @param tax         IR se resgatar tudo nesse mês
     * @param invested    investimento líquido (bruto menos o IR)
     */
    public record Point(YearMonth month, BigDecimal current, BigDecimal simulated, BigDecimal cash,
                        BigDecimal contributed, BigDecimal interest, BigDecimal tax, BigDecimal invested) {
    }

    /**
     * @param firstNegativeCurrent   1º mês com saldo negativo sem mudanças (nulo = nunca)
     * @param firstNegativeSimulated 1º mês com saldo simulado negativo
     * @param monthlyRate            rendimento ao mês usado (%), nulo sem investimento
     */
    public record Summary(BigDecimal finalCurrent, BigDecimal finalSimulated, BigDecimal difference,
                          BigDecimal contributed, BigDecimal interest, BigDecimal tax, BigDecimal invested,
                          YearMonth firstNegativeCurrent, YearMonth firstNegativeSimulated,
                          BigDecimal monthlyRate, BigDecimal yearlyRate) {
    }

    /** @param real valores em reais de hoje (descontado o IPCA projetado) */
    public record Result(YearMonth start, boolean real, Base base, Rates rates, Summary summary, List<Point> points) {
    }

    public record CompareItem(UUID id, String name, Summary summary, List<Point> points) {
    }
}
