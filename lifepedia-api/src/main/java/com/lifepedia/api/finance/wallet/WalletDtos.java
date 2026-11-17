package com.lifepedia.api.finance.wallet;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.bank.BankRef;
import com.lifepedia.api.user.UserDtos.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class WalletDtos {

    private WalletDtos() {
    }

    /** @param accountId conta que paga a fatura (nula = a do mesmo banco) */
    public record CardRequest(
            @NotNull(message = "Informe o banco.") @Valid BankRef bank,
            @Size(max = 40, message = "Apelido muito longo.") String nickname,
            @Pattern(regexp = "^\\d{4}$", message = "Informe os 4 últimos números.") String lastDigits,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida.") String color,
            @NotNull(message = "Informe o dia do fechamento.") @Min(value = 1, message = "Dia inválido.")
            @Max(value = 31, message = "Dia inválido.") Integer closingDay,
            @NotNull(message = "Informe o dia do vencimento.") @Min(value = 1, message = "Dia inválido.")
            @Max(value = 31, message = "Dia inválido.") Integer dueDay,
            @DecimalMin(value = "0.01", message = "O limite deve ser maior que zero.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal creditLimit,
            @DecimalMin(value = "0.01", message = "A meta deve ser maior que zero.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal budget,
            Boolean reminders,
            UUID accountId,
            @Size(max = 20, message = "Escolha no máximo 20 grupos.") List<@NotNull UUID> groupIds) {
    }

    /** @param balanceOn dia do saldo (padrão: hoje) */
    public record AccountRequest(
            @NotNull(message = "Informe o banco.") @Valid BankRef bank,
            @Size(max = 40, message = "Apelido muito longo.") String nickname,
            @NotNull(message = "Informe o saldo.") @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal balance,
            LocalDate balanceOn) {
    }

    /** Saldo de hoje, sem mudar o resto da conta. */
    public record BalanceRequest(
            @NotNull(message = "Informe o saldo.") @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal balance,
            LocalDate balanceOn) {
    }

    /** Tudo opcional: dia (hoje), valor (o da fatura) e conta (a que paga o cartão). */
    public record PayRequest(
            LocalDate paidOn,
            @DecimalMin(value = "0.00", message = "Valor inválido.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            UUID accountId) {
    }

    /**
     * Lançamento rápido num cartão (vai para a fatura certa, parcelas nas seguintes) ou numa conta.
     *
     * @param amount     valor total (dividido entre as parcelas)
     * @param categoryId nula = a do último lançamento com a mesma descrição, ou "Outros"
     */
    public record PurchaseRequest(
            UUID cardId,
            UUID accountId,
            @NotBlank(message = "Informe a descrição.") @Size(max = 100, message = "Máximo de 100 caracteres.") String description,
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            LocalDate date,
            UUID categoryId,
            @Min(value = 1, message = "Parcelas inválidas.") @Max(value = 48, message = "No máximo 48 parcelas.") Integer installments,
            @Size(max = 20) List<@NotNull UUID> groupIds) {
    }

    public record PurchaseResult(int created, YearMonth firstMonth, UUID categoryId) {
    }

    public record RecalculateRequest(
            @NotNull(message = "Informe o mês.") YearMonth from,
            @NotEmpty(message = "Escolha ao menos um lançamento.")
            @Size(max = 500, message = "No máximo 500 por vez.") List<@NotNull UUID> ids) {
    }

    /** Lançamento à mão que está numa fatura diferente da que o ciclo indica. */
    public record RecalculateItem(UUID id, String description, LocalDate date, BigDecimal amount, YearMonth month,
                                  YearMonth expected) {
    }

    public record RecalculateResult(int moved) {
    }

    /**
     * Uma fatura resumida.
     *
     * @param daysToClose dias até o fechamento (negativo: já fechou)
     * @param daysToDue   dias até o vencimento (negativo: venceu)
     */
    public record InvoiceSummary(YearMonth month, InvoiceStatus status, LocalDate opensOn, LocalDate closesOn, LocalDate dueOn,
                                 BigDecimal total, LocalDate paidOn, BigDecimal paidAmount, long daysToClose, long daysToDue) {
    }

    /** @param used faturas não pagas + parcelas já lançadas nas próximas */
    public record Limit(BigDecimal limit, BigDecimal used, BigDecimal available) {
    }

    public record GroupRef(UUID id, String name) {
    }

    /**
     * Cartão com a fatura aberta e, se houver, a fechada ainda não paga.
     *
     * @param key     chave do banco ({@code BankRef.key()}), para filtrar os lançamentos
     * @param pending fatura anterior fechada e não paga (nula se não há)
     */
    public record CardView(UUID id, String key, BankRef bank, String label, String nickname, String lastDigits, String color,
                           int closingDay, int dueDay, BigDecimal creditLimit, BigDecimal budget, boolean reminders,
                           UUID accountId, List<GroupRef> groups, InvoiceSummary current, InvoiceSummary pending, Limit limit) {
    }

    /**
     * Conta com o saldo informado e o projetado.
     *
     * @param today          saldo informado + o que entrou e saiu depois, até hoje
     * @param endOfMonth     {@code today} + o que ainda vem no mês, com as faturas a pagar
     * @param pendingInvoices faturas desta conta a pagar até o fim do mês
     */
    public record AccountView(UUID id, String key, BankRef bank, String label, String nickname, BigDecimal balance,
                              LocalDate balanceOn, BigDecimal today, BigDecimal endOfMonth, BigDecimal pendingInvoices) {
    }

    /** @param openTotal soma das faturas abertas; {@code toPay} = fechadas ainda não pagas */
    public record Wallet(List<CardView> cards, List<AccountView> accounts, BigDecimal openTotal, BigDecimal toPay,
                         LocalDate today) {
    }

    /**
     * Item da fatura: ocasional ({@code transactionId}) ou fixo ({@code recurringId}).
     *
     * @param installment parcela ("3/10"), se houver
     */
    public record InvoiceItem(UUID transactionId, UUID recurringId, String description, LocalDate date, EntryType type,
                              BigDecimal amount, String category, String color, boolean checked, String installment) {
    }

    /**
     * Previsão da fatura aberta.
     *
     * @param projected     quanto deve fechar, pelo ritmo de compras do ciclo (nula no começo do ciclo)
     * @param average       média das 3 últimas faturas com valor
     * @param budgetPercent quanto da meta já foi usado
     */
    public record Projection(BigDecimal projected, BigDecimal average, long cycleDays, long elapsedDays, Integer budgetPercent) {
    }

    public record MonthTotal(YearMonth month, BigDecimal total, InvoiceStatus status) {
    }

    /**
     * Uma fatura com os itens.
     *
     * @param purchases compras feitas no ciclo
     * @param committed fixos, parcelas e compras de antes do ciclo
     * @param credits   estornos e créditos (já descontados do total)
     */
    public record InvoiceDetail(InvoiceSummary summary, List<InvoiceItem> items, int checked, int count, BigDecimal purchases,
                                BigDecimal committed, BigDecimal credits, Projection projection) {
    }

    /** @param history 6 faturas antes da mostrada; {@code upcoming} = 6 depois */
    public record CardDetail(CardView card, InvoiceDetail invoice, List<MonthTotal> history, List<MonthTotal> upcoming) {
    }

    /**
     * Cartão de um membro mostrado no grupo.
     *
     * @param groupSpent gastos do grupo lançados por esse membro neste cartão, na fatura aberta
     */
    public record GroupCard(UUID id, UserSummary owner, boolean mine, String label, BankRef bank, String lastDigits, String color,
                            InvoiceSummary current, BigDecimal groupSpent) {
    }

    /** Carteira no arquivo da conta (só registro): cartões com os pagamentos e contas com o último saldo. */
    public record WalletData(List<CardData> cards, List<BankAccountData> accounts) {
    }

    public record CardData(String bank, String nickname, String lastDigits, String color, int closingDay, int dueDay,
                           BigDecimal creditLimit, BigDecimal budget, boolean reminders, List<PaymentData> payments) {
    }

    public record PaymentData(YearMonth month, LocalDate paidOn, BigDecimal amount) {
    }

    public record BankAccountData(String bank, String nickname, BigDecimal balance, LocalDate balanceOn) {
    }
}
