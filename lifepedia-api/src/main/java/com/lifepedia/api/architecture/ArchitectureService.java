package com.lifepedia.api.architecture;

import com.lifepedia.api.architecture.ArchVersion.Status;
import com.lifepedia.api.architecture.ArchitectureAccess.Access;
import com.lifepedia.api.architecture.ArchitectureAccess.Grant;
import com.lifepedia.api.architecture.ArchitectureDtos.ArchitectureExport;
import com.lifepedia.api.architecture.ArchitectureDtos.CommentExport;
import com.lifepedia.api.architecture.ArchitectureDtos.CurrentRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.NewVersionRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.PendingVersion;
import com.lifepedia.api.architecture.ArchitectureDtos.ProjectDetail;
import com.lifepedia.api.architecture.ArchitectureDtos.ProjectExport;
import com.lifepedia.api.architecture.ArchitectureDtos.ProjectList;
import com.lifepedia.api.architecture.ArchitectureDtos.ProjectRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.ProjectSummary;
import com.lifepedia.api.architecture.ArchitectureDtos.Usage;
import com.lifepedia.api.architecture.ArchitectureDtos.VersionExport;
import com.lifepedia.api.architecture.ArchitectureDtos.VersionResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.VersionUpdate;
import com.lifepedia.api.architecture.ArchitectureDtos.ViewResponse;
import com.lifepedia.api.architecture.ArchitectureFiles.Received;
import com.lifepedia.api.architecture.ArchitectureFiles.Type;
import com.lifepedia.api.config.ArchitectureProperties;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.shared.image.Images;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Projetos, versões da maquete, capa e espaço de cada conta. */
@Service
public class ArchitectureService {

    static final int MAX_PROJECTS = 20;
    static final int MAX_VERSIONS = 30;
    static final int MAX_PENDING = 3;
    static final int COVER_SIDE = 960;
    static final Duration PENDING_TTL = Duration.ofHours(2);
    private static final int UPLOADS_PER_HOUR = 20;

    private final ArchProjectRepository projects;
    private final ArchVersionRepository versions;
    private final ArchViewRepository views;
    private final ArchAttachmentRepository attachments;
    private final ArchCommentRepository comments;
    private final ArchitectureAccess access;
    private final ArchitectureFiles files;
    private final UserRepository users;
    private final ArchitectureProperties properties;
    private final RateLimiter rateLimiter;
    private final TransactionTemplate transaction;
    private final ArchSceneService scene;
    private final Clock clock;

    public ArchitectureService(ArchProjectRepository projects, ArchVersionRepository versions, ArchViewRepository views,
                               ArchAttachmentRepository attachments, ArchCommentRepository comments,
                               ArchitectureAccess access, ArchitectureFiles files, UserRepository users,
                               ArchitectureProperties properties, RateLimiter rateLimiter,
                               PlatformTransactionManager transactions, ArchSceneService scene, Clock clock) {
        this.scene = scene;
        this.projects = projects;
        this.versions = versions;
        this.views = views;
        this.attachments = attachments;
        this.comments = comments;
        this.access = access;
        this.files = files;
        this.users = users;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProjectList list(UUID userId) {
        List<ArchProject> owned = projects.findByUserIdOrderByUpdatedAtDesc(userId);
        Map<UUID, Grant> grants = access.grants(userId);
        List<ArchProject> shared = grants.isEmpty() ? List.of()
                : projects.findByIdInAndUserIdNotOrderByUpdatedAtDesc(grants.keySet(), userId);
        List<ArchProject> all = Stream.concat(owned.stream(), shared.stream()).toList();
        Map<UUID, ArchVersion> current = versions.findByIdIn(all.stream().map(ArchProject::getCurrentVersionId)
                        .filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(ArchVersion::getId, Function.identity()));
        Map<UUID, UserSummary> people = summaries(all.stream().map(ArchProject::getUserId).toList());
        return new ProjectList(
                owned.stream().map(p -> summary(p, ArchRole.OWNER, null, current, people)).toList(),
                shared.stream().map(p -> {
                    Grant grant = grants.get(p.getId());
                    return summary(p, grant.role(), grant.groupName(), current, people);
                }).toList(),
                usage(userId));
    }

    @Transactional
    public ProjectDetail create(UUID userId, ProjectRequest request) {
        if (projects.countByUserId(userId) >= MAX_PROJECTS) {
            throw bad("Limite de " + MAX_PROJECTS + " projetos atingido.");
        }
        ArchProject project = new ArchProject(userId, request.name().trim());
        apply(project, request);
        projects.save(project);
        return detail(new Access(project, ArchRole.OWNER));
    }

    @Transactional(readOnly = true)
    public ProjectDetail detail(UUID projectId, UUID userId) {
        return detail(access.require(projectId, userId, ArchRole.VIEW));
    }

    @Transactional
    public ProjectDetail update(UUID projectId, UUID userId, ProjectRequest request) {
        Access found = access.require(projectId, userId, ArchRole.EDIT);
        found.project().setName(request.name().trim());
        apply(found.project(), request);
        return detail(found);
    }

    /** Apaga o projeto e, depois do commit, os arquivos dele. */
    @Transactional
    public void delete(UUID projectId, UUID userId) {
        ArchProject project = access.require(projectId, userId, ArchRole.OWNER).project();
        versions.keysOfProject(projectId).forEach(key -> files.deleteAfterCommit(key, Type.MODEL));
        attachments.findByProjectIdOrderByCreatedAtDesc(projectId)
                .forEach(a -> files.deleteAfterCommit(a.getFileKey(), a.fileType()));
        files.deleteAfterCommit(project.getCoverKey(), Type.IMAGE);
        projects.delete(project);
    }

    /** Capa do projeto (foto da maquete tirada no navegador); devolve a URL nova. */
    @Transactional
    public String cover(UUID projectId, UUID userId, byte[] upload) {
        ArchProject project = access.require(projectId, userId, ArchRole.EDIT).project();
        String previous = project.getCoverKey();
        project.setCoverKey(files.save(Images.fromUpload(upload, COVER_SIDE), Type.IMAGE));
        files.deleteAfterCommit(previous, Type.IMAGE);
        return coverUrl(project);
    }

    @Transactional(readOnly = true)
    public Resource coverFile(UUID projectId, UUID userId) {
        ArchProject project = access.require(projectId, userId, ArchRole.VIEW).project();
        if (project.getCoverKey() == null) throw new NotFoundException("Projeto sem capa.");
        return files.resource(project.getCoverKey(), Type.IMAGE);
    }

    /** Reserva o espaço da versão nova (conta no limite até o arquivo chegar ou ela vencer). */
    @Transactional
    public PendingVersion startVersion(UUID projectId, UUID userId, NewVersionRequest request) {
        ArchProject project = access.require(projectId, userId, ArchRole.EDIT).project();
        long maxModel = properties.maxModel().toBytes();
        if (request.sizeBytes() > maxModel) {
            throw bad("A maquete comprimida passou de " + properties.maxModel().toMegabytes() + " MB. Tire objetos que não"
                    + " precisam aparecer ou diminua as texturas no SketchUp e tente de novo.");
        }
        if (versions.countByProjectIdAndStatus(projectId, Status.READY) >= MAX_VERSIONS) {
            throw bad("Limite de " + MAX_VERSIONS + " versões por projeto. Exclua versões antigas.");
        }
        if (versions.countByProjectIdAndStatus(projectId, Status.PENDING) >= MAX_PENDING) {
            throw new BusinessException(HttpStatus.CONFLICT, "Já há envios em andamento neste projeto. Espere terminar.");
        }
        requireRoom(project.getUserId(), request.sizeBytes());
        ArchVersion version = new ArchVersion(projectId, userId, request.sizeBytes(), blankToNull(request.sourceName()),
                request.sourceBytes());
        version.setLabel(blankToNull(request.label()));
        version.setNote(blankToNull(request.note()));
        versions.save(version);
        return new PendingVersion(version.getId(), "/api/architecture/versions/" + version.getId() + "/model");
    }

    /**
     * Recebe o GLB de uma versão reservada por quem a criou: grava fora de transação (arquivo grande) e só então
     * marca a versão como pronta e atual. Se algo falhar depois de gravar, o arquivo é apagado.
     */
    public VersionResponse uploadModel(UUID versionId, UUID userId, InputStream body) {
        if (!rateLimiter.tryAcquire("arch-upload:" + userId, UPLOADS_PER_HOUR, Duration.ofHours(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitos envios seguidos. Tente de novo mais tarde.");
        }
        long declared = Objects.requireNonNull(transaction.execute(status -> pending(versionId, userId).getSizeBytes()));
        Received received = files.receiveModel(body, declared);
        try {
            return transaction.execute(status -> {
                ArchVersion version = pending(versionId, userId);
                ArchProject project = projects.findById(version.getProjectId()).orElseThrow(ArchitectureService::notFound);
                version.ready(versions.maxNumber(project.getId()) + 1, received.key(), received.size());
                project.setCurrentVersionId(version.getId());
                project.touch();
                versions.flush();
                return versionResponse(version, summaries(List.of(userId)));
            });
        } catch (RuntimeException e) {
            files.delete(received.key(), Type.MODEL);
            throw e;
        }
    }

    @Transactional
    public VersionResponse updateVersion(UUID versionId, UUID userId, VersionUpdate request) {
        ArchVersion version = ready(versionId, userId, ArchRole.EDIT);
        version.setLabel(blankToNull(request.label()));
        version.setNote(blankToNull(request.note()));
        return versionResponse(version, summaries(nonNull(version.getCreatedBy())));
    }

    @Transactional
    public ProjectDetail setCurrent(UUID projectId, UUID userId, CurrentRequest request) {
        Access found = access.require(projectId, userId, ArchRole.EDIT);
        ArchVersion version = versions.findById(request.versionId())
                .filter(v -> v.isReady() && v.getProjectId().equals(projectId))
                .orElseThrow(() -> bad("Versão inválida."));
        found.project().setCurrentVersionId(version.getId());
        return detail(found);
    }

    /** Exclui a versão (e os comentários dela); a atual passa a ser a mais nova que sobrar. */
    @Transactional
    public void deleteVersion(UUID versionId, UUID userId) {
        ArchVersion version = ready(versionId, userId, ArchRole.EDIT);
        ArchProject project = projects.findById(version.getProjectId()).orElseThrow(ArchitectureService::notFound);
        files.deleteAfterCommit(version.getFileKey(), Type.MODEL);
        versions.delete(version);
        versions.flush();
        if (version.getId().equals(project.getCurrentVersionId())) {
            project.setCurrentVersionId(versions.findFirstByProjectIdAndStatusOrderByNumberDesc(project.getId(), Status.READY)
                    .map(ArchVersion::getId).orElse(null));
        }
        project.touch();
    }

    @Transactional(readOnly = true)
    public Resource model(UUID versionId, UUID userId) {
        return files.resource(ready(versionId, userId, ArchRole.VIEW).getFileKey(), Type.MODEL);
    }

    /** Reservas cujo arquivo não chegou a tempo (o envio caiu ou a aba fechou). */
    @Transactional
    public int purgePending() {
        List<ArchVersion> stale = versions.findByStatusAndCreatedAtBefore(Status.PENDING, OffsetDateTime.now(clock).minus(PENDING_TTL));
        versions.deleteAll(stale);
        return stale.size();
    }

    /** Conta excluída: o banco apaga os projetos em cascata; aqui, depois do commit, os arquivos deles. */
    public void deleteUserFilesAfterCommit(UUID userId) {
        versions.keysOfUser(userId).forEach(key -> files.deleteAfterCommit(key, Type.MODEL));
        projects.coverKeysOfUser(userId).forEach(key -> files.deleteAfterCommit(key, Type.IMAGE));
        attachments.ofUserProjects(userId)
                .forEach(a -> files.deleteAfterCommit(a.getFileKey(), a.fileType()));
    }

    /** Para o arquivo da conta: projetos da pessoa (versões, vistas, anexos, variantes e cômodos) e comentários dela. */
    @Transactional(readOnly = true)
    public ArchitectureExport exportData(UUID userId) {
        List<ArchProject> owned = projects.findByUserIdOrderByUpdatedAtDesc(userId);
        List<ProjectExport> list = owned.stream().map(p -> new ProjectExport(p.getName(), p.getDescription(), p.getLatitude(),
                p.getNorthDegrees(), p.getCreatedAt(),
                versions.findByProjectIdAndStatusOrderByNumberDesc(p.getId(), Status.READY).stream()
                        .map(v -> new VersionExport(v.getNumber(), v.getLabel(), v.getNote(), v.getSizeBytes(), v.getSourceName(),
                                v.getCreatedAt())).toList(),
                views.findByProjectIdOrderByCreatedAtAsc(p.getId()).stream().map(ArchView::getName).toList(),
                attachments.findByProjectIdOrderByCreatedAtDesc(p.getId()).stream().map(ArchAttachment::getName).toList(),
                scene.variantNames(p.getId()), scene.roomNames(p.getId()))).toList();
        List<ArchComment> mine = comments.findByUserIdOrderByCreatedAtAsc(userId);
        Map<UUID, String> names = projects.findAllById(mine.stream().map(ArchComment::getProjectId).distinct().toList()).stream()
                .collect(Collectors.toMap(ArchProject::getId, ArchProject::getName));
        return new ArchitectureExport(list, mine.stream().map(c -> new CommentExport(names.get(c.getProjectId()), c.getBody(),
                c.getResolvedAt() != null, c.getCreatedAt())).toList());
    }

    /** Espaço do dono; usado também pelos anexos. */
    public Usage usage(UUID ownerId) {
        return new Usage(projects.usedBytes(ownerId), properties.quota().toBytes(), properties.maxModel().toBytes());
    }

    /** Barra arquivo novo que não cabe no espaço do dono. */
    public void requireRoom(UUID ownerId, long bytes) {
        Usage usage = usage(ownerId);
        if (usage.usedBytes() + bytes > usage.quotaBytes()) {
            throw bad("Sem espaço: o dono do projeto usa " + megabytes(usage.usedBytes()) + " de " + megabytes(usage.quotaBytes())
                    + ". Exclua versões ou anexos antigos.");
        }
    }

    List<ViewResponse> viewResponses(UUID projectId) {
        return views.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(v -> new ViewResponse(v.getId(), v.getName(), v.position(), v.target())).toList();
    }

    Map<UUID, UserSummary> summaries(Collection<UUID> ids) {
        return users.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, UserSummary::from));
    }

    static VersionResponse versionResponse(ArchVersion v, Map<UUID, UserSummary> people) {
        return new VersionResponse(v.getId(), v.getNumber(), v.getLabel(), v.getNote(), v.getSizeBytes(), v.getSourceName(),
                v.getSourceBytes(), v.getCreatedBy() == null ? null : people.get(v.getCreatedBy()), v.getCreatedAt(),
                "/api/architecture/versions/" + v.getId() + "/model");
    }

    static String coverUrl(ArchProject project) {
        return project.getCoverKey() == null ? null
                : "/api/architecture/projects/" + project.getId() + "/cover?v=" + project.getCoverKey();
    }

    static BusinessException bad(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private ProjectDetail detail(Access found) {
        ArchProject p = found.project();
        List<ArchVersion> ready = versions.findByProjectIdAndStatusOrderByNumberDesc(p.getId(), Status.READY);
        Map<UUID, UserSummary> people = summaries(Stream.concat(Stream.of(p.getUserId()),
                ready.stream().map(ArchVersion::getCreatedBy).filter(Objects::nonNull)).toList());
        return new ProjectDetail(p.getId(), p.getName(), p.getDescription(), p.getLatitude(), p.getNorthDegrees(), found.role(),
                people.get(p.getUserId()), coverUrl(p), p.getCurrentVersionId(),
                ready.stream().map(v -> versionResponse(v, people)).toList(), viewResponses(p.getId()),
                attachments.countByProjectId(p.getId()), comments.countByProjectIdAndResolvedAtIsNull(p.getId()),
                found.can(ArchRole.EDIT) ? usage(p.getUserId()) : null, p.getUpdatedAt());
    }

    private static ProjectSummary summary(ArchProject p, ArchRole role, String sharedVia, Map<UUID, ArchVersion> current,
                                          Map<UUID, UserSummary> people) {
        ArchVersion version = p.getCurrentVersionId() == null ? null : current.get(p.getCurrentVersionId());
        return new ProjectSummary(p.getId(), p.getName(), p.getDescription(), role, people.get(p.getUserId()), coverUrl(p),
                version == null ? null : versionResponse(version, Map.of()), p.getUpdatedAt(), sharedVia);
    }

    private static void apply(ArchProject project, ProjectRequest request) {
        project.setDescription(blankToNull(request.description()));
        project.setLatitude(request.latitude());
        project.setNorthDegrees(request.northDegrees() == null ? 0 : request.northDegrees());
    }

    private ArchVersion pending(UUID versionId, UUID userId) {
        ArchVersion version = versions.findById(versionId).orElseThrow(ArchitectureService::notFound);
        access.require(version.getProjectId(), userId, ArchRole.EDIT);
        if (!userId.equals(version.getCreatedBy())) throw notFound();
        if (version.isReady()) throw new BusinessException(HttpStatus.CONFLICT, "Esta versão já recebeu a maquete.");
        return version;
    }

    private ArchVersion ready(UUID versionId, UUID userId, ArchRole needed) {
        ArchVersion version = versions.findById(versionId).filter(ArchVersion::isReady)
                .orElseThrow(ArchitectureService::notFound);
        access.require(version.getProjectId(), userId, needed);
        return version;
    }

    private static List<UUID> nonNull(UUID id) {
        return id == null ? List.of() : List.of(id);
    }

    private static String megabytes(long bytes) {
        return Math.round(bytes / 1024.0 / 1024.0) + " MB";
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Versão não encontrada.");
    }
}
