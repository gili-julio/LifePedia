package com.lifepedia.api.dev;

import com.lifepedia.api.dev.DevItem.Kind;
import com.lifepedia.api.integration.IntegrationDtos.EventResponse;
import com.lifepedia.api.integration.IntegrationProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DevDtos {

    static final int MAX_LINKS = 12;

    private DevDtos() {
    }

    public enum LinkKind { REPO, BOARD, STAGING, PRODUCTION, DOCS, CHAT, OTHER }

    public record Link(
            @NotBlank(message = "Dê um nome ao atalho.") @Size(max = 40, message = "Nome do atalho com até 40 caracteres.") String label,
            @NotBlank(message = "Informe o endereço.") @Size(max = 500, message = "Endereço longo demais.")
            @Pattern(regexp = "^https?://\\S+$", message = "O endereço precisa começar com http:// ou https://.") String url,
            @NotNull(message = "Escolha o tipo do atalho.") LinkKind kind) {
    }

    public record ProjectRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 60, message = "Nome com até 60 caracteres.") String name,
            @NotNull(message = "Escolha uma cor.") @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida.") String color,
            @Size(max = 300, message = "Descrição com até 300 caracteres.") String description,
            @Size(max = MAX_LINKS, message = "Até 12 atalhos.") List<@NotNull @Valid Link> links,
            UUID calendarAccountId) {
    }

    public record OrderRequest(@NotNull(message = "Informe a ordem.") @Size(max = 100) List<@NotNull UUID> ids) {
    }

    public record RepoRequest(
            @NotNull(message = "Escolha a conta.") UUID accountId,
            @NotBlank(message = "Escolha o repositório.") @Size(max = 200) String externalId) {
    }

    /** Raias: etiqueta posta na issue quando o MR dela abre e quando é aceito; {@code labelsClear} saem nessas horas. */
    public record RulesRequest(
            @NotNull(message = "Informe se as issues viram tarefas.") Boolean importIssues,
            @NotNull(message = "Informe se concluir fecha a issue.") Boolean closeIssues,
            @Size(max = 100, message = "Etiqueta com até 100 caracteres.") String labelMrOpened,
            @Size(max = 100, message = "Etiqueta com até 100 caracteres.") String labelMrMerged,
            @Size(max = 10, message = "Até 10 etiquetas para tirar.")
            List<@NotBlank @Size(max = 100, message = "Etiqueta com até 100 caracteres.") String> labelsClear) {
    }

    public record RepoResponse(UUID id, UUID accountId, String accountName, String accountColor, IntegrationProvider provider,
                               boolean accountActive, boolean canWrite, String fullName, String webUrl, String defaultBranch,
                               boolean importIssues, boolean closeIssues, String labelMrOpened, String labelMrMerged,
                               List<String> labelsClear, OffsetDateTime syncedAt) {
    }

    public record Counts(int myMergeRequests, int reviews, int issues, int failing) {
    }

    public record ProjectSummary(UUID id, String name, String color, String description, List<Link> links, UUID calendarAccountId,
                                 List<RepoResponse> repos, Counts counts) {
    }

    /** MR, issue ou pipeline; {@code linkedIssues} = issues que o MR fecha ("repo#número"). */
    public record ItemResponse(UUID id, UUID projectId, String projectName, String projectColor, UUID repoId, String repoName,
                               Kind kind, String number, String title, String state, String webUrl, String author, boolean mine,
                               boolean review, boolean draft, String pipeline, String branch, List<String> labels, int comments,
                               boolean approved, List<String> linkedIssues, LocalDate dueDate, UUID taskId,
                               OffsetDateTime updatedAt, OffsetDateTime mergedAt) {
    }

    public record DayActivity(LocalDate day, int commits, int mergeRequests, int merged, int reviews) {
    }

    public record ProjectActivity(UUID id, String name, String color, int commits, int mergeRequests, int merged, int reviews) {
    }

    public record ActivityResponse(List<DayActivity> days, List<ProjectActivity> projects) {
    }

    /** {@code events} = de hoje aos próximos 7 dias, da conta Google do projeto. */
    public record ProjectDetail(ProjectSummary project, List<ItemResponse> items, List<EventResponse> events,
                                List<DayActivity> activity) {
    }

    public record AccountProblem(UUID id, IntegrationProvider provider, String nickname) {
    }

    /** Painel "Hoje no dev". */
    public record Today(LocalDate date, List<EventResponse> events, List<ItemResponse> myMergeRequests, List<ItemResponse> reviews,
                        List<ItemResponse> failing, List<ItemResponse> issues, List<AccountProblem> problems, boolean aiEnabled,
                        int projects) {
    }

    public record RepoOption(String externalId, String fullName, String webUrl, boolean linked) {
    }

    /** Resumo para o daily, montado pela IA; {@code text} pronto para colar. */
    public record Daily(List<String> yesterday, List<String> today, List<String> blockers, String text) {
    }

    /** Registro no arquivo da conta. */
    public record ProjectExport(String name, String description, List<Link> links, List<String> repos) {
    }
}
