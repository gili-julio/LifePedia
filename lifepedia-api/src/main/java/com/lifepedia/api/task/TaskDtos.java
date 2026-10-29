package com.lifepedia.api.task;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lifepedia.api.task.TaskOccurrence.Status;
import com.lifepedia.api.user.UserDtos.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TaskDtos {

    private TaskDtos() {
    }

    /**
     * @param weekdays       1 = segunda ... 7 = domingo (só semanal; vazio = dia do início)
     * @param monthMode      só mensal (padrão: mesmo dia do mês)
     * @param fromCompletion a próxima conta a partir de quando concluiu (sem dias da semana/modo do mês)
     */
    public record RepeatRule(
            @NotNull(message = "Escolha a frequência.") Recurrence.Unit unit,
            @NotNull(message = "Informe o intervalo.") @Min(value = 1, message = "Intervalo mínimo: 1.")
            @Max(value = 999, message = "Intervalo máximo: 999.") Integer interval,
            @Size(max = 7) List<@NotNull @Min(1) @Max(7) Integer> weekdays,
            Recurrence.MonthMode monthMode,
            Boolean fromCompletion,
            LocalDate until,
            @Min(value = 1, message = "Mínimo de 1 vez.") @Max(value = 1000, message = "Máximo de 1000 vezes.") Integer count) {
    }

    /** @param id nulo = subtarefa nova */
    public record SubtaskInput(
            UUID id,
            @NotBlank(message = "Informe a subtarefa.") @Size(max = 200, message = "Máximo de 200 caracteres.") String title) {
    }

    /**
     * @param dueDate          nulo = sem data; com repetição é o início
     * @param assignees        responsáveis fixos (só grupo)
     * @param allMembers       todos do grupo, inclusive quem entrar depois (no lugar dos responsáveis fixos)
     * @param rotation         rodízio em ordem (só grupo, com repetição; no lugar dos responsáveis)
     * @param completion       com mais de um responsável: basta um concluir (padrão) ou cada um conclui a sua
     * @param reminderMinutes  lembrete N minutos antes do horário (0 = na hora)
     * @param recurringEntryId fixo do financeiro ligado (só pessoal)
     */
    public record TaskRequest(
            @NotBlank(message = "Informe a tarefa.") @Size(max = 200, message = "Máximo de 200 caracteres.") String title,
            @Size(max = 10000, message = "Máximo de 10000 caracteres.") String notes,
            Priority priority,
            LocalDate dueDate,
            LocalTime dueTime,
            @Min(value = 1, message = "Duração mínima: 1 minuto.") @Max(value = 1440, message = "Duração máxima: 24 horas.")
            Integer durationMinutes,
            UUID listId,
            @Size(max = 10, message = "Máximo de 10 etiquetas.") Set<@NotNull UUID> tagIds,
            Boolean habit,
            @Valid RepeatRule repeat,
            @Size(max = 50, message = "Máximo de 50 subtarefas.") List<@Valid @NotNull SubtaskInput> subtasks,
            @Size(max = 50, message = "Máximo de 50 responsáveis.") List<@NotNull UUID> assignees,
            Boolean allMembers,
            @Size(max = 50, message = "Máximo de 50 pessoas no rodízio.") List<@NotNull UUID> rotation,
            Completion completion,
            @Min(value = 0, message = "Lembrete inválido.") @Max(value = 1440, message = "Lembrete de até 1 dia antes.")
            Integer reminderMinutes,
            UUID recurringEntryId) {
    }

    public record Subtask(UUID id, String title) {
    }

    /** Tarefa completa (para editar). {@code canDelete}: criador ou administrador do grupo. */
    public record TaskResponse(
            UUID id, String title, String notes, Priority priority, LocalDate dueDate,
            @JsonFormat(pattern = "HH:mm") LocalTime dueTime, Integer durationMinutes, UUID listId, Set<UUID> tagIds,
            boolean habit, RepeatRule repeat, List<Subtask> subtasks, OffsetDateTime completedAt,
            UUID groupId, UUID createdBy, List<UUID> assignees, boolean allMembers, List<UUID> rotation,
            Completion completion, boolean canDelete, Integer reminderMinutes, UUID recurringEntryId) {
    }

    public record SubtaskState(UUID id, String title, boolean done) {
    }

    /**
     * Uma ocorrência na agenda.
     *
     * @param date          chave da ocorrência (dia pela regra; nulo = sem data)
     * @param scheduledDate dia em que aparece (diferente de {@code date} se adiada só esta)
     * @param edited        título/notas/horário/prioridade mudados só nesta
     * @param groupId       nulo = pessoal (na visão pessoal vêm também as de grupo em que a pessoa é responsável)
     * @param assignees     responsáveis desta ocorrência (trocado só nesta, vez do rodízio, todos ou os fixos)
     * @param doneBy        "cada um conclui a sua": quem já fez a sua parte
     * @param streak        sequência do hábito (nulo se não for hábito)
     */
    public record TaskItem(
            UUID taskId, LocalDate date, LocalDate scheduledDate, String title, String notes, Priority priority,
            @JsonFormat(pattern = "HH:mm") LocalTime dueTime, Integer durationMinutes, Status status, boolean overdue,
            OffsetDateTime completedAt, UUID listId, Set<UUID> tagIds, List<SubtaskState> subtasks,
            RepeatRule repeat, boolean habit, boolean edited, UUID groupId, List<UUID> assignees, boolean allMembers,
            boolean rotating, Completion completion, List<UUID> doneBy, UUID createdBy, UUID completedBy,
            long commentCount, boolean canDelete, Integer streak, Integer reminderMinutes, UUID recurringEntryId) {
    }

    public record Day(LocalDate date, List<TaskItem> items) {
    }

    /**
     * Ocorrência que ocupa o mesmo horário (sem duração conta 30 min).
     *
     * @param people  de quem é o choque entre as pessoas consultadas (vazio = tarefa do grupo sem responsável)
     * @param groupId nulo = tarefa pessoal de quem consultou
     */
    public record Conflict(UUID taskId, LocalDate date, String title, @JsonFormat(pattern = "HH:mm") LocalTime dueTime,
                           Integer durationMinutes, UUID groupId, List<UUID> people) {
    }

    /**
     * Horas visíveis na agenda ({@code dayEnd} exclusivo: 24 = até meia-noite).
     *
     * @param sleepMinutes sono de segunda a domingo, em minutos; nulo ao salvar = mantém o atual
     */
    public record AgendaSettings(
            @NotNull(message = "Informe o início.") @Min(value = 0, message = "Início inválido.")
            @Max(value = 23, message = "Início inválido.") Integer dayStart,
            @NotNull(message = "Informe o fim.") @Min(value = 1, message = "Fim inválido.")
            @Max(value = 24, message = "Fim inválido.") Integer dayEnd,
            @Size(min = 7, max = 7, message = "Informe o sono dos 7 dias.")
            List<@NotNull(message = "Informe o sono do dia.") @Min(value = 0, message = "Sono inválido.")
                @Max(value = 960, message = "No máximo 16 horas de sono.") Integer> sleepMinutes) {
    }

    public record GroupMoveRequest(@NotNull(message = "Escolha o grupo.") UUID groupId) {
    }

    /** @param overdue pendentes antes de {@code from} (só com {@code overdue=true}) */
    public record Agenda(LocalDate today, List<TaskItem> overdue, List<Day> days) {
    }

    /**
     * Ocorrência concluída.
     *
     * @param completedDay dia da conclusão no fuso do app (agrupa a lista)
     * @param dayCount     quantas foram concluídas nesse dia (todas, não só as desta página)
     */
    public record HistoryItem(UUID taskId, LocalDate date, String title, UUID listId, OffsetDateTime completedAt,
                              LocalDate completedDay, long dayCount, boolean recurring, UUID completedBy) {
    }

    /** Um dia na faixa do hábito ({@code state} nulo = não tinha ocorrência). */
    public record HabitDay(LocalDate date, HabitStats.Day state) {
    }

    /**
     * Hábito com sequência, % do mês e os últimos 7 dias.
     *
     * @param start início da regra (para descrever a repetição)
     */
    public record HabitSummary(UUID taskId, String title, UUID listId, UUID groupId, RepeatRule repeat, LocalDate start,
                               int streak, int best, Integer monthRate, int monthDone, int monthDue,
                               List<HabitDay> days) {
    }

    /** Responsável só desta ocorrência ({@code assigneeId} nulo = volta ao da tarefa). */
    public record AssignRequest(LocalDate date, UUID assigneeId) {
    }

    public record CommentRequest(
            @NotBlank(message = "Escreva o comentário.") @Size(max = 2000, message = "Máximo de 2000 caracteres.") String body) {
    }

    /** @param author nulo se a conta foi excluída */
    public record CommentResponse(UUID id, UserSummary author, String body, OffsetDateTime createdAt, boolean canDelete) {
    }

    /** @param date ocorrência (nulo para tarefa sem data) */
    public record StatusRequest(LocalDate date, @NotNull(message = "Informe o status.") Status status) {
    }

    public record OverdueRequest(@NotNull(message = "Informe o status.") Status status) {
    }

    public record OverdueResult(int updated) {
    }

    /** @param to novo dia (nulo = sem data, só tarefa sem repetição) */
    public record MoveRequest(LocalDate date, LocalDate to) {
    }

    /** Edição "só esta ocorrência". */
    public record OccurrenceRequest(
            @NotNull(message = "Informe a ocorrência.") LocalDate date,
            @NotNull(message = "Informe o dia.") LocalDate moveTo,
            @NotBlank(message = "Informe a tarefa.") @Size(max = 200, message = "Máximo de 200 caracteres.") String title,
            @Size(max = 10000, message = "Máximo de 10000 caracteres.") String notes,
            LocalTime dueTime,
            Priority priority) {
    }

    public record CheckRequest(LocalDate date, @NotNull Boolean done) {
    }

    public record OrderRequest(@NotEmpty @Size(max = 500) List<@NotNull UUID> taskIds) {
    }

    public enum Scope { THIS, FOLLOWING, ALL }

    public enum Sort { MANUAL, PRIORITY, TIME }

    public record LabelRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 60, message = "Máximo de 60 caracteres.") String name,
            @NotNull(message = "Escolha a cor.")
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida (use o formato #RRGGBB).") String color) {
    }

    /** Lista ou etiqueta; {@code openCount} = tarefas não concluídas com ela. */
    public record Label(UUID id, String name, String color, long openCount) {
    }

    public record Labels(List<Label> lists, List<Label> tags) {
    }

    /** Arquivo da conta: lista ou etiqueta pessoal ({@code ref} liga as tarefas). */
    public record LabelData(
            @NotBlank @Size(max = 64) String ref,
            @NotBlank(message = "Lista/etiqueta sem nome.") @Size(max = 60) String name,
            @NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida.") String color) {
    }

    /**
     * Arquivo da conta: tarefa pessoal com o que mudou em cada ocorrência.
     *
     * @param list  {@code ref} da lista
     * @param tags  {@code ref}s das etiquetas
     * @param fixed {@code ref} do fixo do financeiro
     */
    public record TaskData(
            @NotBlank(message = "Tarefa sem título.") @Size(max = 200) String title,
            @Size(max = 10000) String notes,
            Priority priority,
            LocalDate dueDate,
            LocalTime dueTime,
            @Min(1) @Max(1440) Integer durationMinutes,
            @Min(0) @Max(1440) Integer reminderMinutes,
            @Size(max = 64) String list,
            @Size(max = 10) List<@NotBlank @Size(max = 64) String> tags,
            Boolean habit,
            @Valid RepeatRule repeat,
            OffsetDateTime completedAt,
            @Size(max = 64) String fixed,
            @Size(max = 50) List<@NotBlank @Size(max = 200) String> subtasks,
            @Size(max = 20_000) List<@NotNull @Valid OccurrenceData> occurrences) {
    }

    /** @param checked posições (a partir de 0) das subtarefas marcadas */
    public record OccurrenceData(
            LocalDate date,
            @NotNull Status status,
            LocalDate movedTo,
            @Size(max = 200) String title,
            @Size(max = 10000) String notes,
            LocalTime dueTime,
            Priority priority,
            OffsetDateTime completedAt,
            @Size(max = 50) List<@NotNull @Min(0) @Max(49) Integer> checked) {
    }
}
