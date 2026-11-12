package com.lifepedia.api.integration;

import com.lifepedia.api.integration.IntegrationAccount.Status;
import com.lifepedia.api.integration.IntegrationSync.Outcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class IntegrationDtos {

    static final String COLOR = "^#[0-9a-fA-F]{6}$";
    static final String COLOR_MESSAGE = "Cor inválida.";

    private IntegrationDtos() {
    }

    /** @param unavailable motivo de não dar para conectar neste servidor (nulo = disponível) */
    public record ProviderInfo(IntegrationProvider provider, boolean available, String unavailable) {
    }

    /** @param doneToday a última lição da ofensiva foi hoje */
    public record DuolingoStats(int streak, long totalXp, int bestStreak, LocalDate lastLesson, boolean doneToday) {
    }

    /** Conta ligada, sem token. {@code projects} = nomes dos projetos do Dev que usam a conta. */
    public record AccountResponse(UUID id, IntegrationProvider provider, String nickname, String color, String baseUrl,
                                  String login, Status status, OffsetDateTime lastSyncAt, String lastError,
                                  LocalDate tokenExpiresAt, boolean canWrite, DuolingoStats duolingo, List<String> projects,
                                  OffsetDateTime createdAt) {
    }

    public record Overview(List<ProviderInfo> providers, List<AccountResponse> accounts) {
    }

    /** Para o Início e o perfil. */
    public record Summary(int connected, int problems, DuolingoStats duolingo) {
    }

    /** GitLab ({@code baseUrl} vazio = gitlab.com) ou GitHub. */
    public record TokenRequest(
            @Size(max = 200, message = "Endereço longo demais.") String baseUrl,
            @NotBlank(message = "Cole o token.") @Size(max = 500, message = "Token longo demais.") String token,
            @Size(max = 40, message = "Apelido com até 40 caracteres.") String nickname,
            @Pattern(regexp = COLOR, message = COLOR_MESSAGE) String color) {
    }

    public record DuolingoRequest(
            @NotBlank(message = "Informe o usuário do Duolingo.")
            @Pattern(regexp = "^[A-Za-z0-9_.-]{1,60}$", message = "Usuário do Duolingo inválido.") String username,
            @Size(max = 40, message = "Apelido com até 40 caracteres.") String nickname,
            @Pattern(regexp = COLOR, message = COLOR_MESSAGE) String color) {
    }

    public record LabelRequest(
            @NotBlank(message = "Informe um apelido.") @Size(max = 40, message = "Apelido com até 40 caracteres.") String nickname,
            @NotNull(message = "Escolha uma cor.") @Pattern(regexp = COLOR, message = COLOR_MESSAGE) String color) {
    }

    public record ReconnectRequest(
            @NotBlank(message = "Cole o token.") @Size(max = 500, message = "Token longo demais.") String token) {
    }

    public record GoogleStart(String url) {
    }

    public record SyncEntry(OffsetDateTime at, Outcome outcome, boolean manual, int items, String message, long durationMs) {
    }

    public record EventResponse(UUID id, UUID accountId, String accountName, String color, String title, OffsetDateTime start,
                                OffsetDateTime end, boolean allDay, String location, String meetUrl, String htmlUrl) {
    }

    /** Registro no arquivo da conta (sem token). */
    public record AccountExport(IntegrationProvider provider, String nickname, String login, String baseUrl,
                                OffsetDateTime connectedAt) {
    }
}
