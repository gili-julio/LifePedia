package com.lifepedia.api.account;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.planning.PlanItem;
import com.lifepedia.api.task.TaskDtos.LabelData;
import com.lifepedia.api.task.TaskDtos.TaskData;
import com.lifepedia.api.user.UserDtos.UserSummary;
import jakarta.validation.Valid;
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
import java.util.Set;
import java.util.UUID;

public final class AccountDtos {

    /** Versão do arquivo exportado; mudou o formato? Crie {@code @3} e mantenha a importação das anteriores. */
    public static final String FORMAT = "lifepedia/account@2";
    /** Versões aceitas na importação ({@code @1}: sem tarefas). */
    public static final Set<String> IMPORTABLE = Set.of("lifepedia/account@1", FORMAT);

    private AccountDtos() {
    }

    /** @param googleCredential ID token recente do Google, no lugar da senha em conta criada pelo Google */
    public record DeleteRequest(
            @Size(max = 200) String password,
            @Size(max = 4096) String googleCredential,
            @NotNull(message = "Digite EXCLUIR para confirmar.")
            @Pattern(regexp = "EXCLUIR", message = "Digite EXCLUIR para confirmar.") String confirmation) {
    }

    /** O que some com a conta. */
    public record DeletionPreview(long categories, long transactions, long recurring, long tasks, List<GroupImpact> groups) {
    }

    /**
     * @param deletesGroup era o último membro: o grupo é excluído
     * @param newAdmin     quem vira admin (nulo = ninguém muda)
     */
    public record GroupImpact(UUID id, String name, int otherMembers, boolean deletesGroup, UserSummary newAdmin,
                              long transactions, long recurring, long planItems) {
    }

    /**
     * Arquivo de exportação. Categorias, fixos, listas e etiquetas são ligados pelo {@code ref}; {@code groups} é só
     * registro (não importa).
     *
     * @param profile ignorado na importação
     * @param tasks   tarefas pessoais (nulo no {@code @1}: a importação não mexe nas tarefas)
     */
    public record AccountData(
            @NotNull(message = "Arquivo sem versão.") String format,
            OffsetDateTime exportedAt,
            Profile profile,
            @NotNull(message = "Arquivo sem categorias.") @Size(max = 1_000, message = "Categorias demais no arquivo.")
            List<@NotNull @Valid CategoryData> categories,
            @NotNull(message = "Arquivo sem fixos.") @Size(max = 5_000, message = "Fixos demais no arquivo.")
            List<@NotNull @Valid RecurringData> recurring,
            @NotNull(message = "Arquivo sem lançamentos.") @Size(max = 200_000, message = "Lançamentos demais no arquivo.")
            List<@NotNull @Valid TransactionData> transactions,
            List<GroupData> groups,
            @Size(max = 500, message = "Listas demais no arquivo.") List<@NotNull @Valid LabelData> taskLists,
            @Size(max = 500, message = "Etiquetas demais no arquivo.") List<@NotNull @Valid LabelData> taskTags,
            @Size(max = 50_000, message = "Tarefas demais no arquivo.") List<@NotNull @Valid TaskData> tasks) {
    }

    public record Profile(String name, String username, String email) {
    }

    public record CategoryData(
            @NotBlank @Size(max = 64) String ref,
            @NotBlank(message = "Categoria sem nome.") @Size(max = 60) String name,
            @NotNull EntryType type,
            @NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida.") String color) {
    }

    /** @param ref liga as tarefas ao fixo (nulo no {@code @1}) */
    public record RecurringData(
            @Size(max = 64) String ref,
            @NotBlank(message = "Fixo sem descrição.") @Size(max = 120) String description,
            @NotNull EntryType type,
            @NotBlank @Size(max = 64) String category,
            @NotNull @DecimalMin(value = "0.01", message = "Valor inválido.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            @Min(1) @Max(31) Integer dayOfMonth,
            @NotNull YearMonth startMonth,
            YearMonth endMonth,
            Boolean active,
            @Size(max = 10000) String notes,
            @Size(max = 1_200) List<@NotNull @Valid AdjustmentData> adjustments) {
    }

    public record AdjustmentData(
            @NotNull YearMonth month,
            @NotNull @DecimalMin(value = "0.01", message = "Valor inválido.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount) {
    }

    public record TransactionData(
            @NotBlank(message = "Lançamento sem descrição.") @Size(max = 120) String description,
            @Size(max = 200) String originalDescription,
            @NotNull EntryType type,
            @NotBlank @Size(max = 64) String category,
            @NotNull @DecimalMin(value = "0.01", message = "Valor inválido.")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido.") BigDecimal amount,
            @NotNull LocalDate date,
            @NotNull YearMonth month,
            @Size(max = 10000) String notes) {
    }

    /** Itens da pessoa num grupo, como estavam na exportação. */
    public record GroupData(String name, GroupMember.Role role, List<GroupTransactionData> transactions,
                            List<GroupRecurringData> recurring, List<PlanItemData> planItems) {
    }

    public record GroupTransactionData(String description, EntryType type, String category, BigDecimal amount,
                                       LocalDate date, YearMonth month, String notes, boolean synced) {
    }

    public record GroupRecurringData(String description, EntryType type, String category, BigDecimal amount,
                                     Integer dayOfMonth, YearMonth startMonth, YearMonth endMonth, boolean active,
                                     String notes, boolean synced) {
    }

    public record PlanItemData(String name, PlanItem.Kind kind, BigDecimal amount, YearMonth targetMonth,
                               int installments, boolean active, String notes, String url, LocalDate acquiredOn,
                               BigDecimal acquiredAmount) {
    }

    /** MERGE soma aos dados atuais; REPLACE apaga antes o financeiro pessoal (e as tarefas pessoais, se o arquivo tiver). */
    public enum ImportMode { MERGE, REPLACE }

    /** @param dryRun só calcula o resultado (prévia), sem gravar */
    public record ImportRequest(
            @NotNull(message = "Escolha como importar.") ImportMode mode,
            Boolean dryRun,
            @NotNull(message = "Envie o arquivo.") @Valid AccountData data) {

        public boolean dryRunOrFalse() {
            return Boolean.TRUE.equals(dryRun);
        }
    }

    /**
     * @param skipped         iguais a itens que já existem (só no MERGE)
     * @param removed         apagados antes (só no REPLACE)
     * @param syncedDetached  itens sincronizados em grupos que viraram cópia (REPLACE)
     */
    public record ImportResult(boolean applied, Counts created, Counts skipped, Counts removed, int categoriesReused,
                               int syncedDetached) {
    }

    /** @param taskLabels listas + etiquetas das tarefas */
    public record Counts(int categories, int transactions, int recurring, int adjustments, int tasks, int taskLabels) {

        static final Counts NONE = new Counts(0, 0, 0, 0, 0, 0);
    }
}
