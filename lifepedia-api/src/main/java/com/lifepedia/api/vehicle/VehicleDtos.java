package com.lifepedia.api.vehicle;

import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.vehicle.VehicleMath.Due;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class VehicleDtos {

    static final int MAX_KM = 9_999_999;

    private VehicleDtos() {
    }

    /**
     * @param odometer só no cadastro: vira a primeira leitura (hoje)
     * @param template só no cadastro: já cria os itens da tabela sugerida do tipo
     */
    public record VehicleRequest(
            @NotNull(message = "Escolha o tipo.") VehicleType type,
            @NotBlank(message = "Dê um nome ao veículo.") @Size(max = 60) String name,
            @Size(max = 60) String brand,
            @Size(max = 120) String model,
            @Min(value = 1900, message = "Ano inválido.") @Max(value = 2100, message = "Ano inválido.") Integer modelYear,
            @Size(max = 10) String plate,
            @Size(max = 30) String color,
            VehicleFuel fuel,
            @DecimalMin(value = "1", message = "Tanque inválido.") @DecimalMax(value = "2000", message = "Tanque inválido.")
            @Digits(integer = 4, fraction = 1, message = "Tanque inválido.") BigDecimal tankLiters,
            @Min(value = 0, message = "Km inválido.") @Max(value = MAX_KM, message = "Km inválido.") Integer odometer,
            @Size(max = 2000) String notes,
            @Valid FipeRef fipe,
            Boolean template) {
    }

    public record FipeRef(
            @NotNull FipeKind kind,
            @NotBlank @Pattern(regexp = "\\d{1,10}") String brand,
            @NotBlank @Pattern(regexp = "\\d{1,10}") String model,
            @NotBlank @Pattern(regexp = "\\d{4}-\\d{1,2}") String year) {
    }

    public record ArchiveRequest(@NotNull Boolean archived) {
    }

    public record ShareRequest(@NotNull(message = "Escolha a permissão.") VehicleRole role) {
    }

    public record ReadingRequest(
            @NotNull(message = "Informe o km.") @Min(value = 0, message = "Km inválido.") @Max(value = MAX_KM, message = "Km inválido.")
            Integer km,
            @NotNull(message = "Informe a data.") LocalDate date) {
    }

    /** Lançar também no financeiro de quem lançou; {@code groupIds} só na criação (itens sincronizados). */
    public record FinanceLink(
            @NotNull(message = "Escolha a categoria.") UUID categoryId,
            @Size(max = 20) List<UUID> groupIds) {
    }

    public record ExpenseRequest(
            @NotNull(message = "Escolha o tipo.") ExpenseKind kind,
            @NotNull(message = "Informe a data.") LocalDate date,
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @Digits(integer = 10, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            @Size(max = 120) String description,
            @Min(value = 0, message = "Km inválido.") @Max(value = MAX_KM, message = "Km inválido.") Integer odometer,
            @DecimalMin(value = "0.01", message = "Litros inválidos.") @Digits(integer = 5, fraction = 2, message = "Litros inválidos.")
            BigDecimal liters,
            VehicleFuel fuel,
            Boolean fullTank,
            @Size(max = 60) String station,
            @Size(max = 2000) String notes,
            @Valid FinanceLink finance) {
    }

    public record ItemRequest(
            @NotBlank(message = "Dê um nome.") @Size(max = 60) String name,
            @Min(value = 1, message = "Intervalo inválido.") @Max(value = 1_000_000, message = "Intervalo inválido.") Integer intervalKm,
            @Min(value = 1, message = "Intervalo inválido.") @Max(value = 240, message = "Intervalo inválido.") Integer intervalMonths,
            @Min(value = 0, message = "Km inválido.") @Max(value = MAX_KM, message = "Km inválido.") Integer lastKm,
            LocalDate lastOn,
            Boolean alert) {
    }

    /**
     * Feito (item ou revisão). {@code amount} lança um gasto de manutenção; {@code itemIds} (só revisão) marca os itens
     * feitos junto.
     */
    public record DoneRequest(
            @NotNull(message = "Informe a data.") LocalDate date,
            @Min(value = 0, message = "Km inválido.") @Max(value = MAX_KM, message = "Km inválido.") Integer km,
            @Size(max = 500) String notes,
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @Digits(integer = 10, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            @Size(max = 60) String shop,
            @Valid FinanceLink finance,
            @Size(max = 40) List<UUID> itemIds) {
    }

    public record RevisionRequest(
            @NotBlank(message = "Dê um nome.") @Size(max = 60) String name,
            @Min(value = 0, message = "Km inválido.") @Max(value = MAX_KM, message = "Km inválido.") Integer dueKm,
            LocalDate dueOn,
            @Size(max = 2000) String notes) {
    }

    /** Gera revisões a cada {@code everyKm} e/ou {@code everyMonths} (meses contados de {@code since}). */
    public record PlanRequest(
            @Min(value = 500, message = "Intervalo inválido.") @Max(value = 100_000, message = "Intervalo inválido.") Integer everyKm,
            @Min(value = 1, message = "Intervalo inválido.") @Max(value = 60, message = "Intervalo inválido.") Integer everyMonths,
            LocalDate since,
            @NotNull(message = "Quantas revisões?") @Min(value = 1, message = "De 1 a 20 revisões.")
            @Max(value = 20, message = "De 1 a 20 revisões.")
            Integer count) {
    }

    public record DocumentRequest(
            @NotNull(message = "Escolha o tipo.") DocumentKind kind,
            @NotBlank(message = "Dê um nome.") @Size(max = 60) String name,
            @NotNull(message = "Informe o vencimento.") LocalDate dueOn,
            @DecimalMin(value = "0.01", message = "Valor inválido.") @Digits(integer = 10, fraction = 2, message = "Valor inválido.")
            BigDecimal amount,
            @Size(max = 2000) String notes) {
    }

    /** Pago/renovado: próximo vencimento e, com {@code amount}, o gasto lançado em {@code paidOn} (padrão hoje). */
    public record RenewRequest(
            @NotNull(message = "Informe o próximo vencimento.") LocalDate nextDue,
            LocalDate paidOn,
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @Digits(integer = 10, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            @Valid FinanceLink finance) {
    }

    public record GroupRef(UUID id, String name, String imageUrl) {
    }

    public enum UpcomingKind {
        ITEM, REVISION, DOCUMENT
    }

    public record Upcoming(UpcomingKind kind, UUID id, String name, Due due) {
    }

    public record FipeInfo(FipeKind kind, String brand, String model, String year, String code, BigDecimal value, String month,
                           OffsetDateTime checkedAt) {
    }

    /**
     * Veículo na lista e no topo do detalhe.
     *
     * @param estimatedKm km de hoje pelo ritmo de uso
     * @param next        vencimento mais próximo (item, revisão ou documento)
     * @param alerts      quantos estão perto de vencer ou vencidos
     * @param sharedVia   nome do grupo quando o acesso vem só por ele
     */
    public record VehicleCard(UUID id, VehicleType type, String name, String brand, String model, Integer modelYear, String plate,
                              String color, VehicleFuel fuel, String imageUrl, int odometer, LocalDate odometerOn, int estimatedKm,
                              Double kmPerDay, Upcoming next, int alerts, BigDecimal fipeValue, UserSummary owner, boolean mine,
                              VehicleRole role, String sharedVia, OffsetDateTime archivedAt) {
    }

    public record VehicleList(List<VehicleCard> owned, List<VehicleCard> shared) {
    }

    public record ItemResponse(UUID id, String name, Integer intervalKm, Integer intervalMonths, Integer lastKm, LocalDate lastOn,
                               boolean alert, Due due) {
    }

    /** @param due nulo quando feita */
    public record RevisionResponse(UUID id, String name, Integer dueKm, LocalDate dueOn, LocalDate doneOn, Integer doneKm, String shop,
                                   String notes, UUID expenseId, Due due) {
    }

    public record DocumentResponse(UUID id, DocumentKind kind, String name, LocalDate dueOn, BigDecimal amount, String notes, Due due) {
    }

    /** @param upcoming todos os vencimentos, do mais urgente ao mais distante */
    public record VehicleDetail(VehicleCard vehicle, BigDecimal tankLiters, String notes, FipeInfo fipe, List<ItemResponse> items,
                                List<RevisionResponse> revisions, List<DocumentResponse> documents, List<Upcoming> upcoming) {
    }

    public record ReadingResponse(UUID id, int km, LocalDate date, UserSummary user, boolean fromExpense, boolean canDelete) {
    }

    /** @param finance só para quem lançou e se foi para o financeiro */
    public record FinanceInfo(UUID transactionId, UUID categoryId) {
    }

    public record ExpenseResponse(UUID id, ExpenseKind kind, LocalDate date, BigDecimal amount, String description, Integer odometer,
                                  BigDecimal liters, BigDecimal pricePerLiter, VehicleFuel fuel, boolean fullTank, String station,
                                  String notes, String imageUrl, UserSummary user, boolean mine, boolean canDelete,
                                  FinanceInfo finance) {
    }

    public record LogResponse(UUID id, String name, LocalDate date, Integer km, String notes, UserSummary user, UUID expenseId,
                              BigDecimal amount, UUID itemId, UUID revisionId) {
    }

    public record MonthStat(YearMonth month, BigDecimal fuel, BigDecimal maintenance, BigDecimal documents, BigDecimal other,
                            BigDecimal total) {
    }

    public record ConsumptionPoint(LocalDate date, BigDecimal kmPerLiter, int km, BigDecimal liters) {
    }

    /**
     * Números do período ({@code months} meses até o atual).
     *
     * @param costPerKm      gasto total do período / km rodados nele (nulo sem km)
     * @param avgKmPerLiter  média dos trechos de tanque cheio do período
     * @param avgFuelPrice   preço médio do litro nos abastecimentos com litros
     */
    public record Stats(int months, List<MonthStat> byMonth, List<ConsumptionPoint> consumption, BigDecimal total, Integer kmDriven,
                        BigDecimal costPerKm, BigDecimal avgKmPerLiter, BigDecimal lastKmPerLiter, BigDecimal avgFuelPrice,
                        BigDecimal liters) {
    }

    public record SummaryItem(UUID vehicleId, String vehicleName, VehicleType type, Upcoming upcoming) {
    }

    /** Para o Início: veículos ativos e os vencimentos perto ou vencidos (até 5). */
    public record Summary(int vehicles, List<SummaryItem> upcoming) {
    }

    public record UserShare(UserSummary user, VehicleRole role) {
    }

    public record GroupShare(GroupRef group, VehicleRole role) {
    }

    public record Shares(List<UserShare> users, List<GroupShare> groups) {
    }

    public record ShareOptions(List<UserSummary> friends, List<GroupRef> groups) {
    }

    public record FipeOption(String code, String name) {
    }

    public record FipePrice(String code, BigDecimal value, String brand, String model, Integer modelYear, String fuel, String month) {
    }

    public record Template(String name, Integer intervalKm, Integer intervalMonths) {
    }

    /** Arquivo da conta (só registro): veículos da pessoa com tudo o que foi lançado neles. */
    public record VehicleData(VehicleType type, String name, String brand, String model, Integer modelYear, String plate,
                              VehicleFuel fuel, int odometer, String fipeCode, BigDecimal fipeValue, OffsetDateTime archivedAt,
                              List<ReadingData> readings, List<ExpenseData> expenses, List<ItemData> items,
                              List<RevisionData> revisions, List<DocumentData> documents) {
    }

    public record ReadingData(LocalDate date, int km) {
    }

    public record ExpenseData(ExpenseKind kind, LocalDate date, BigDecimal amount, String description, Integer odometer,
                              BigDecimal liters, VehicleFuel fuel, boolean fullTank, String station, String notes, boolean mine) {
    }

    public record ItemData(String name, Integer intervalKm, Integer intervalMonths, Integer lastKm, LocalDate lastOn) {
    }

    public record RevisionData(String name, Integer dueKm, LocalDate dueOn, LocalDate doneOn, Integer doneKm, String shop) {
    }

    public record DocumentData(DocumentKind kind, String name, LocalDate dueOn, BigDecimal amount) {
    }
}
