package com.lifepedia.api.admin;

import com.lifepedia.api.admin.Announcement.Level;
import com.lifepedia.api.admin.AuditEntry.Action;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminDtos {

    private AdminDtos() {
    }

    /** Lista de usuários: todos, só admins ou só bloqueados. */
    public enum UserFilter { ALL, ADMIN, BLOCKED }

    /** Quem recebe a notificação em massa (contas bloqueadas nunca recebem). */
    public enum Audience { ALL, ADMINS }

    /**
     * Números do topo do painel; ativos = último acesso na janela.
     *
     * @param signupsDaily  cadastros por dia nos últimos 30 dias (fuso {@code TIME_ZONE}), dias sem cadastro com 0
     * @param signupsWeekly cadastros por semana (segunda-feira) nas últimas 26
     */
    public record Overview(long totalUsers, long newUsers7, long newUsers30, long activeUsers7, long activeUsers30,
                           long admins, long blocked, List<DayCount> signupsDaily, List<DayCount> signupsWeekly) {
    }

    public record DayCount(LocalDate date, long count) {
    }

    /** @param key módulo ({@code FINANCE}, {@code TASKS}...); texto no front */
    public record ModuleUsage(String key, long users, long items) {
    }

    public record Modules(long totalUsers, List<ModuleUsage> modules) {
    }

    /**
     * Uso da IA da importação.
     *
     * @param last24h  leituras terminadas nas últimas 24 h (o limite diário conta as tentativas, na memória)
     * @param running  leituras rodando agora
     * @param outcomes resultado das leituras em 30 dias ({@code SUCCESS} ou o motivo da falha)
     */
    public record AiOverview(boolean enabled, String model, long timeoutSeconds, int dailyLimit, long last24h,
                             long running, long users30, long avgDurationMs, Map<String, Long> outcomes,
                             List<AiDay> daily) {
    }

    public record AiDay(LocalDate date, long success, long failed) {
    }

    /**
     * Situação do servidor.
     *
     * @param version versão da build; nula fora do jar
     */
    public record SystemStatus(String version, Instant buildTime, Instant startedAt, long uptimeSeconds, String javaVersion,
                               String timeZone, long memoryUsedMb, long memoryMaxMb, Database database, Features features,
                               List<Job> jobs) {
    }

    /** @param schemaVersion última migration aplicada */
    public record Database(boolean up, long latencyMs, String version, long sizeBytes, String schemaVersion) {
    }

    public record Features(boolean ai, String aiModel, boolean push, boolean googleLogin, boolean fixedAdmin) {
    }

    /** @param status {@code NONE} (ainda não rodou), {@code STARTED}, {@code SUCCESS} ou {@code ERROR} */
    public record Job(String name, Instant lastRun, String status, Instant nextRun) {
    }

    /**
     * Linha da lista de usuários.
     *
     * @param fixedAdmin conta do {@code ADMIN_EMAIL}: sempre admin, não pode ser bloqueada
     * @param self       é quem está vendo (não tira o próprio admin nem se bloqueia)
     */
    public record AdminUser(UUID id, String name, String username, String email, String imageUrl, OffsetDateTime createdAt,
                            OffsetDateTime lastSeenAt, boolean admin, boolean blocked, boolean fixedAdmin, boolean self) {
    }

    /**
     * Detalhe do usuário: só cadastro e contagens, nunca conteúdo.
     *
     * @param termsCurrent aceitou a versão vigente dos termos
     * @param counts       itens por módulo (chaves no front)
     */
    public record AdminUserDetail(AdminUser user, boolean googleLinked, boolean hasPassword, String termsVersion,
                                  OffsetDateTime termsAcceptedAt, boolean termsCurrent, OffsetDateTime blockedAt,
                                  long activeSessions, long pushDevices, Map<String, Long> counts) {
    }

    public record AdminRequest(@NotNull(message = "Informe se a conta é admin.") Boolean admin) {
    }

    public record BlockRequest(@NotNull(message = "Informe se a conta fica bloqueada.") Boolean blocked) {
    }

    public record AuditResponse(UUID id, Action action, UUID actorId, String actorName, UUID targetId, String targetName,
                                String detail, OffsetDateTime createdAt) {

        static AuditResponse from(AuditEntry entry) {
            return new AuditResponse(entry.getId(), entry.getAction(), entry.getActorId(), entry.getActorName(),
                    entry.getTargetId(), entry.getTargetName(), entry.getDetail(), entry.getCreatedAt());
        }
    }

    /** @param endsAt fim do aviso; nulo = até encerrar */
    public record AnnouncementRequest(
            @NotBlank(message = "Escreva o aviso.") @Size(max = 500, message = "Aviso com até 500 caracteres.") String message,
            @NotNull(message = "Escolha o tipo do aviso.") Level level,
            OffsetDateTime endsAt) {
    }

    /** @param createdByName nulo se a conta de quem criou foi excluída */
    public record AnnouncementResponse(UUID id, String message, Level level, OffsetDateTime endsAt, OffsetDateTime endedAt,
                                       boolean active, String createdByName, OffsetDateTime createdAt) {
    }

    /** O que todos veem no app. */
    public record ActiveAnnouncement(UUID id, String message, Level level, OffsetDateTime endsAt) {

        static ActiveAnnouncement from(Announcement a) {
            return new ActiveAnnouncement(a.getId(), a.getMessage(), a.getLevel(), a.getEndsAt());
        }
    }

    public record BroadcastRequest(
            @NotBlank(message = "Escreva o título.") @Size(max = 80, message = "Título com até 80 caracteres.") String title,
            @NotBlank(message = "Escreva a mensagem.") @Size(max = 300, message = "Mensagem com até 300 caracteres.") String body,
            @NotNull(message = "Escolha quem recebe.") Audience audience) {
    }

    public record BroadcastResponse(int recipients) {
    }
}
