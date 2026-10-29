package com.lifepedia.api.note;

import com.lifepedia.api.note.NoteActivity.Action;
import com.lifepedia.api.user.UserDtos.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class NoteDtos {

    private NoteDtos() {
    }

    /** Qual lista: em uso, arquivadas, lixeira ou modelos (só pessoal). */
    public enum Status { ACTIVE, ARCHIVED, TRASH, TEMPLATE }

    /**
     * @param template só ao criar (pessoal): vira modelo, fora da lista de notas
     * @param version  ao editar: a versão que a pessoa tinha (diferente da atual → 409)
     */
    public record NoteRequest(
            @NotBlank(message = "Informe o título.") @Size(max = 200, message = "Máximo de 200 caracteres.") String title,
            @NotNull(message = "Informe o conteúdo.")
            @Size(max = NoteService.MAX_CONTENT, message = "Nota grande demais (até 200 mil caracteres).") String content,
            UUID folderId,
            @Size(max = 20, message = "Até 20 etiquetas.") List<@NotNull UUID> tagIds,
            @Size(max = 16, message = "Ícone inválido.") String icon,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida.") String color,
            UUID taskId,
            UUID commitmentId,
            Boolean template,
            Long version) {
    }

    public record PinRequest(@NotNull(message = "Informe se fixa.") Boolean pinned) {
    }

    public record ArchiveRequest(@NotNull(message = "Informe se arquiva.") Boolean archived) {
    }

    public record LinkedTask(UUID id, String title) {
    }

    public record LinkedCommitment(UUID id, String name) {
    }

    /** Item da listagem: sem o conteúdo, só o começo do texto. */
    public record NoteSummary(UUID id, String title, String excerpt, String icon, String color, UUID folderId,
                              List<UUID> tagIds, boolean pinned, OffsetDateTime archivedAt, OffsetDateTime deletedAt,
                              OffsetDateTime updatedAt, UserSummary editedBy, boolean canDelete) {
    }

    /** Título para links {@code [[Nome]]} e para "citada em". */
    public record NoteRef(UUID id, String title, String icon) {
    }

    public record NoteResponse(UUID id, String title, String content, String icon, String color, UUID folderId,
                               List<UUID> tagIds, boolean template, boolean pinned, OffsetDateTime archivedAt,
                               OffsetDateTime deletedAt, LinkedTask task, LinkedCommitment commitment,
                               List<NoteRef> backlinks, UserSummary createdBy, UserSummary editedBy,
                               OffsetDateTime createdAt, OffsetDateTime updatedAt, long version, boolean canDelete) {
    }

    /** {@code at}: última vez (edições seguidas da mesma pessoa somam numa linha). */
    public record Activity(UUID id, Action action, UserSummary user, OffsetDateTime at) {
    }

    public record FolderRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 80, message = "Máximo de 80 caracteres.") String name) {
    }

    public record TagRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 40, message = "Máximo de 40 caracteres.") String name,
            @NotNull(message = "Escolha a cor.")
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida.") String color) {
    }

    /** {@code count}: notas em uso nela. */
    public record Folder(UUID id, String name, long count) {
    }

    public record Tag(UUID id, String name, String color, long count) {
    }

    public record Labels(List<Folder> folders, List<Tag> tags) {
    }

    /** O que dá para ligar: tarefas (pessoais ou do grupo) e compromissos da frequência (só pessoal). */
    public record LinkOptions(List<LinkedTask> tasks, List<LinkedCommitment> commitments) {
    }

    public record ImageResponse(String url) {
    }

    /** Nota no arquivo da conta (só registro). */
    public record NoteData(String title, String content, String folder, List<String> tags, String icon, String color,
                           boolean template, boolean pinned, boolean archived, boolean trashed,
                           OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
}
