package com.lifepedia.api.architecture;

import com.lifepedia.api.architecture.ArchAttachment.Kind;
import com.lifepedia.api.user.UserDtos.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArchitectureDtos {

    private ArchitectureDtos() {
    }

    /** @param latitude para o sol e a sombra; nulo = não informada */
    public record ProjectRequest(
            @NotBlank(message = "Informe o nome do projeto.") @Size(max = 120, message = "Nome com até 120 caracteres.") String name,
            @Size(max = 1000, message = "Descrição com até 1000 caracteres.") String description,
            @DecimalMin(value = "-90", message = "Latitude entre -90 e 90.")
            @DecimalMax(value = "90", message = "Latitude entre -90 e 90.") BigDecimal latitude,
            @Min(value = 0, message = "Norte entre 0 e 359 graus.")
            @Max(value = 359, message = "Norte entre 0 e 359 graus.") Integer northDegrees) {
    }

    /** Espaço do dono do projeto (maquetes + anexos). */
    public record Usage(long usedBytes, long quotaBytes, long maxModelBytes) {
    }

    /** @param sharedVia nome do grupo quando o acesso vem só por ele */
    public record ProjectSummary(UUID id, String name, String description, ArchRole role, UserSummary owner, String coverUrl,
                                 VersionResponse currentVersion, OffsetDateTime updatedAt, String sharedVia) {
    }

    public record ProjectList(List<ProjectSummary> owned, List<ProjectSummary> shared, Usage usage) {
    }

    /** @param usage espaço do dono (só para quem pode subir versão) */
    public record ProjectDetail(UUID id, String name, String description, BigDecimal latitude, int northDegrees, ArchRole role,
                                UserSummary owner, String coverUrl, UUID currentVersionId, List<VersionResponse> versions,
                                List<ViewResponse> views, long attachments, long openComments, Usage usage,
                                OffsetDateTime updatedAt) {
    }

    /** @param sourceName arquivo original (antes da conversão no navegador) */
    public record VersionResponse(UUID id, int number, String label, String note, long sizeBytes, String sourceName,
                                  Long sourceBytes, UserSummary createdBy, OffsetDateTime createdAt, String modelUrl) {
    }

    /**
     * Pede espaço para uma versão nova; o arquivo vai depois em {@code PUT /versions/{id}/model}.
     *
     * @param sizeBytes tamanho do GLB comprimido que será enviado
     */
    public record NewVersionRequest(
            @Size(max = 120, message = "Nome da versão com até 120 caracteres.") String label,
            @Size(max = 1000, message = "Observação com até 1000 caracteres.") String note,
            @NotNull(message = "Informe o tamanho da maquete.") @Positive(message = "Maquete vazia.") Long sizeBytes,
            @Size(max = 255, message = "Nome do arquivo muito longo.") String sourceName,
            @PositiveOrZero Long sourceBytes) {
    }

    public record PendingVersion(UUID id, String uploadUrl) {
    }

    public record VersionUpdate(
            @Size(max = 120, message = "Nome da versão com até 120 caracteres.") String label,
            @Size(max = 1000, message = "Observação com até 1000 caracteres.") String note) {
    }

    public record CurrentRequest(@NotNull(message = "Escolha a versão.") UUID versionId) {
    }

    /** Pontos em metros: {@code [x, y, z]}. */
    public record ViewRequest(
            @NotBlank(message = "Dê um nome para a vista.") @Size(max = 60, message = "Nome com até 60 caracteres.") String name,
            @NotNull @Size(min = 3, max = 3) List<@NotNull Double> position,
            @NotNull @Size(min = 3, max = 3) List<@NotNull Double> target) {
    }

    public record ViewResponse(UUID id, String name, double[] position, double[] target) {
    }

    public record CommentRequest(
            @NotNull(message = "Escolha a versão.") UUID versionId,
            @NotBlank(message = "Escreva o comentário.") @Size(max = 1000, message = "Comentário com até 1000 caracteres.")
            String body,
            @NotNull(message = "Escolha um ponto da maquete.") @Size(min = 3, max = 3) List<@NotNull Double> point) {
    }

    public record CommentResponse(UUID id, UUID versionId, UserSummary author, String body, double[] point, boolean resolved,
                                  OffsetDateTime createdAt, boolean canResolve, boolean canDelete) {
    }

    public record ResolvedRequest(@NotNull Boolean resolved) {
    }

    public record AttachmentResponse(UUID id, Kind kind, String name, long sizeBytes, UserSummary uploadedBy,
                                     OffsetDateTime createdAt, String fileUrl, boolean canDelete) {
    }

    /**
     * Ajuste de um material na variante (o que vier nulo fica como no arquivo).
     *
     * @param texture textura da biblioteca do app (id), aplicada com {@code scale} metros por repetição
     */
    public record MaterialOverride(
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Cor inválida.") String color,
            @Pattern(regexp = "[a-z0-9-]{1,40}", message = "Textura inválida.") String texture,
            @DecimalMin(value = "0.05", message = "Escala entre 0,05 e 20 m.")
            @DecimalMax(value = "20", message = "Escala entre 0,05 e 20 m.") Double scale) {
    }

    /** @param materials índice do material no GLB → ajuste */
    public record VariantRequest(
            @NotNull(message = "Escolha a versão.") UUID versionId,
            @NotBlank(message = "Dê um nome para a variante.") @Size(max = 60, message = "Nome com até 60 caracteres.") String name,
            @NotNull @Size(max = 300, message = "Até 300 materiais por variante.")
            Map<String, @NotNull @Valid MaterialOverride> materials) {
    }

    public record VariantUpdate(
            @NotBlank(message = "Dê um nome para a variante.") @Size(max = 60, message = "Nome com até 60 caracteres.") String name,
            @NotNull @Size(max = 300, message = "Até 300 materiais por variante.")
            Map<String, @NotNull @Valid MaterialOverride> materials) {
    }

    public record VariantResponse(UUID id, UUID versionId, String name, Map<String, MaterialOverride> materials,
                                  OffsetDateTime updatedAt) {
    }

    /**
     * Cômodo achado no navegador a partir de um ponto do piso.
     *
     * @param outline contorno aproximado: pares {@code [x, z]} em metros
     * @param ceiling altura do teto (y), se achada
     */
    public record RoomRequest(
            @NotNull(message = "Escolha a versão.") UUID versionId,
            @NotBlank(message = "Dê um nome para o cômodo.") @Size(max = 60, message = "Nome com até 60 caracteres.") String name,
            @NotNull @Size(min = 3, max = 3) List<@NotNull Double> point,
            Double ceiling,
            @DecimalMin(value = "0", message = "Área inválida.") @DecimalMax(value = "100000", message = "Área inválida.")
            BigDecimal area,
            @NotNull @Size(max = 128, message = "Contorno com até 128 pontos.")
            List<@NotNull @Size(min = 2, max = 2) List<@NotNull Double>> outline) {
    }

    public record RoomUpdate(
            @NotBlank(message = "Dê um nome para o cômodo.") @Size(max = 60, message = "Nome com até 60 caracteres.") String name) {
    }

    public record RoomResponse(UUID id, UUID versionId, String name, double[] point, Double ceiling, BigDecimal area,
                               List<double[]> outline) {
    }

    /** @param intensity multiplicador da luz padrão (0,1 a 5) */
    public record LightRequest(
            @NotNull(message = "Escolha a versão.") UUID versionId,
            @NotNull @Size(min = 3, max = 3) List<@NotNull Double> point,
            @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Cor inválida.") String color,
            @NotNull @DecimalMin(value = "0.1", message = "Intensidade entre 0,1 e 5.")
            @DecimalMax(value = "5", message = "Intensidade entre 0,1 e 5.") Double intensity) {
    }

    public record LightResponse(UUID id, UUID versionId, double[] point, String color, double intensity) {
    }

    /** Tudo o que foi montado em cima de uma versão. */
    public record Scene(List<VariantResponse> variants, List<RoomResponse> rooms, List<LightResponse> lights) {
    }

    public record RoleRequest(@NotNull(message = "Escolha a permissão.") ArchRole role) {
    }

    public record GroupRef(UUID id, String name, String imageUrl) {
    }

    public record UserShare(UserSummary user, ArchRole role) {
    }

    public record GroupShare(GroupRef group, ArchRole role) {
    }

    /** @param token só o dono vê; o link é {@code /maquete/<token>} */
    public record LinkResponse(UUID id, String token, OffsetDateTime expiresAt, OffsetDateTime createdAt) {
    }

    public record Shares(List<UserShare> users, List<GroupShare> groups, List<LinkResponse> links) {
    }

    /** Amigos e grupos do dono que ainda não têm acesso. */
    public record ShareOptions(List<UserSummary> friends, List<GroupRef> groups) {
    }

    public record LinkRequest(
            @NotNull(message = "Escolha a validade.") @Min(value = 1, message = "Validade de 1 a 365 dias.")
            @Max(value = 365, message = "Validade de 1 a 365 dias.") Integer days) {
    }

    /** O que o link público mostra: só a maquete atual, as vistas e a cena dela (variantes, cômodos e luzes). */
    public record PublicProject(String name, String description, String ownerName, BigDecimal latitude, int northDegrees,
                                PublicVersion version, List<ViewResponse> views, Scene scene, OffsetDateTime expiresAt) {
    }

    public record PublicVersion(int number, String label, long sizeBytes, String modelUrl) {
    }

    /** Projetos da pessoa e comentários que ela escreveu, no arquivo da conta (só registro; arquivos 3D não vão). */
    public record ArchitectureExport(List<ProjectExport> projects, List<CommentExport> comments) {
    }

    public record ProjectExport(String name, String description, BigDecimal latitude, int northDegrees,
                                OffsetDateTime createdAt, List<VersionExport> versions, List<String> views,
                                List<String> attachments, List<String> variants, List<String> rooms) {
    }

    public record VersionExport(int number, String label, String note, long sizeBytes, String sourceName,
                                OffsetDateTime createdAt) {
    }

    public record CommentExport(String projectName, String body, boolean resolved, OffsetDateTime createdAt) {
    }
}
