package com.lifepedia.api.architecture;

import com.lifepedia.api.architecture.ArchAttachment.Kind;
import com.lifepedia.api.architecture.ArchitectureAccess.Access;
import com.lifepedia.api.architecture.ArchitectureDtos.AttachmentResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.CommentRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.CommentResponse;
import com.lifepedia.api.architecture.ArchitectureDtos.ViewRequest;
import com.lifepedia.api.architecture.ArchitectureDtos.ViewResponse;
import com.lifepedia.api.architecture.ArchitectureFiles.Type;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Comentários presos na maquete, vistas salvas e anexos (renders, fotos e plantas em PDF). */
@Service
public class ArchCollabService {

    /** Arquivo do anexo e o registro (tipo e nome para o download). */
    public record AttachmentFile(ArchAttachment attachment, Resource resource) {
    }

    static final int MAX_COMMENTS = 500;
    static final int MAX_VIEWS = 30;
    static final int MAX_ATTACHMENTS = 60;
    static final int IMAGE_SIDE = 2048;
    static final int MAX_PDF_BYTES = 15 * 1024 * 1024;
    private static final double MAX_COORDINATE = 100_000;
    private static final int COMMENTS_PER_MINUTE = 20;

    private final ArchCommentRepository comments;
    private final ArchViewRepository views;
    private final ArchAttachmentRepository attachments;
    private final ArchVersionRepository versions;
    private final ArchShareRepository shares;
    private final ArchitectureAccess access;
    private final ArchitectureService architecture;
    private final ArchitectureFiles files;
    private final UserRepository users;
    private final NotificationService notifications;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public ArchCollabService(ArchCommentRepository comments, ArchViewRepository views, ArchAttachmentRepository attachments,
                             ArchVersionRepository versions, ArchShareRepository shares, ArchitectureAccess access,
                             ArchitectureService architecture, ArchitectureFiles files, UserRepository users,
                             NotificationService notifications, RateLimiter rateLimiter, Clock clock) {
        this.comments = comments;
        this.views = views;
        this.attachments = attachments;
        this.versions = versions;
        this.shares = shares;
        this.access = access;
        this.architecture = architecture;
        this.files = files;
        this.users = users;
        this.notifications = notifications;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> comments(UUID projectId, UUID userId, UUID versionId) {
        Access found = access.require(projectId, userId, ArchRole.VIEW);
        requireVersion(projectId, versionId);
        List<ArchComment> list = comments.findByVersionIdOrderByCreatedAtAsc(versionId);
        Map<UUID, UserSummary> people = architecture.summaries(list.stream().map(ArchComment::getUserId).toList());
        return list.stream().map(c -> response(c, found, userId, people)).toList();
    }

    /** Comentário novo; avisa o dono e quem pode editar (menos quem comentou). */
    @Transactional
    public CommentResponse comment(UUID projectId, UUID userId, CommentRequest request) {
        Access found = access.require(projectId, userId, ArchRole.COMMENT);
        requireVersion(projectId, request.versionId());
        if (!rateLimiter.tryAcquire("arch-comment:" + userId, COMMENTS_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitos comentários seguidos.");
        }
        if (comments.countByVersionId(request.versionId()) >= MAX_COMMENTS) {
            throw ArchitectureService.bad("Limite de " + MAX_COMMENTS + " comentários nesta versão.");
        }
        double[] point = point(request.point());
        ArchComment comment = comments.save(new ArchComment(projectId, request.versionId(), userId, request.body().trim(),
                point[0], point[1], point[2]));
        found.project().touch();
        notifyComment(found.project(), userId, comment.getBody());
        return response(comment, found, userId, architecture.summaries(List.of(userId)));
    }

    /** Resolver ou reabrir: quem escreveu ou quem pode editar. */
    @Transactional
    public CommentResponse resolve(UUID projectId, UUID userId, UUID commentId, boolean resolved) {
        Access found = access.require(projectId, userId, ArchRole.COMMENT);
        ArchComment comment = comment(projectId, commentId);
        if (!canChange(comment, found, userId)) throw forbidden();
        comment.setResolvedAt(resolved ? OffsetDateTime.now(clock) : null);
        return response(comment, found, userId, architecture.summaries(List.of(comment.getUserId())));
    }

    @Transactional
    public void deleteComment(UUID projectId, UUID userId, UUID commentId) {
        Access found = access.require(projectId, userId, ArchRole.VIEW);
        ArchComment comment = comment(projectId, commentId);
        if (!canChange(comment, found, userId)) throw forbidden();
        comments.delete(comment);
    }

    @Transactional
    public ViewResponse addView(UUID projectId, UUID userId, ViewRequest request) {
        access.require(projectId, userId, ArchRole.EDIT);
        if (views.countByProjectId(projectId) >= MAX_VIEWS) {
            throw ArchitectureService.bad("Limite de " + MAX_VIEWS + " vistas por projeto.");
        }
        ArchView view = views.save(new ArchView(projectId, userId, request.name().trim(), point(request.position()),
                point(request.target())));
        return new ViewResponse(view.getId(), view.getName(), view.position(), view.target());
    }

    @Transactional
    public void deleteView(UUID projectId, UUID userId, UUID viewId) {
        access.require(projectId, userId, ArchRole.EDIT);
        views.delete(views.findByIdAndProjectId(viewId, projectId).orElseThrow(() -> new NotFoundException("Vista não encontrada.")));
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> attachments(UUID projectId, UUID userId) {
        Access found = access.require(projectId, userId, ArchRole.VIEW);
        List<ArchAttachment> list = attachments.findByProjectIdOrderByCreatedAtDesc(projectId);
        Map<UUID, UserSummary> people = architecture.summaries(list.stream().map(ArchAttachment::getUserId)
                .filter(Objects::nonNull).toList());
        return list.stream().map(a -> response(a, found, people)).toList();
    }

    /** Imagem (JPEG/PNG até 1 MB, regravada em JPEG) ou PDF até 15 MB; conta no espaço do dono. */
    @Transactional
    public AttachmentResponse attach(UUID projectId, UUID userId, String name, byte[] bytes) {
        Access found = access.require(projectId, userId, ArchRole.EDIT);
        if (attachments.countByProjectId(projectId) >= MAX_ATTACHMENTS) {
            throw ArchitectureService.bad("Limite de " + MAX_ATTACHMENTS + " anexos por projeto.");
        }
        boolean pdf = isPdf(bytes);
        if (pdf && bytes.length > MAX_PDF_BYTES) throw ArchitectureService.bad("O PDF deve ter até 15 MB.");
        byte[] content = pdf ? bytes : Images.fromUpload(bytes, IMAGE_SIDE);
        architecture.requireRoom(found.project().getUserId(), content.length);
        String key = files.save(content, pdf ? Type.PDF : Type.IMAGE);
        ArchAttachment attachment = attachments.save(new ArchAttachment(projectId, userId, pdf ? Kind.PDF : Kind.IMAGE,
                cleanName(name, pdf), key, content.length));
        found.project().touch();
        return response(attachment, found, architecture.summaries(List.of(userId)));
    }

    @Transactional
    public void deleteAttachment(UUID projectId, UUID userId, UUID attachmentId) {
        access.require(projectId, userId, ArchRole.EDIT);
        ArchAttachment attachment = attachment(projectId, attachmentId);
        files.deleteAfterCommit(attachment.getFileKey(), attachment.fileType());
        attachments.delete(attachment);
    }

    @Transactional(readOnly = true)
    public AttachmentFile attachmentFile(UUID projectId, UUID userId, UUID attachmentId) {
        access.require(projectId, userId, ArchRole.VIEW);
        ArchAttachment attachment = attachment(projectId, attachmentId);
        return new AttachmentFile(attachment, files.resource(attachment.getFileKey(), attachment.fileType()));
    }

    private void notifyComment(ArchProject project, UUID actorId, String body) {
        Set<UUID> targets = new LinkedHashSet<>();
        targets.add(project.getUserId());
        shares.findByProjectIdOrderByCreatedAtAsc(project.getId()).stream()
                .filter(s -> s.getUserId() != null && s.getRole() == ArchRole.EDIT).forEach(s -> targets.add(s.getUserId()));
        targets.remove(actorId);
        if (targets.isEmpty()) return;
        User actor = users.findById(actorId).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        Map<String, String> data = Map.of("actorName", actor.getName(),
                "actorUsername", actor.getUsername() == null ? "" : actor.getUsername(),
                "projectId", project.getId().toString(),
                "projectName", project.getName(),
                "preview", body.length() > 120 ? body.substring(0, 119) + "…" : body);
        targets.forEach(target -> notifications.notify(target, NotificationType.ARCHITECTURE_COMMENT, actorId, null, data));
    }

    private void requireVersion(UUID projectId, UUID versionId) {
        if (versionId == null || versions.findById(versionId).filter(v -> v.isReady() && v.getProjectId().equals(projectId))
                .isEmpty()) {
            throw ArchitectureService.bad("Versão inválida.");
        }
    }

    private ArchComment comment(UUID projectId, UUID commentId) {
        return comments.findByIdAndProjectId(commentId, projectId)
                .orElseThrow(() -> new NotFoundException("Comentário não encontrado."));
    }

    private ArchAttachment attachment(UUID projectId, UUID attachmentId) {
        return attachments.findById(attachmentId).filter(a -> a.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("Anexo não encontrado."));
    }

    private static boolean canChange(ArchComment comment, Access found, UUID userId) {
        return comment.getUserId().equals(userId) || found.can(ArchRole.EDIT);
    }

    private static CommentResponse response(ArchComment c, Access found, UUID userId, Map<UUID, UserSummary> people) {
        boolean change = canChange(c, found, userId);
        return new CommentResponse(c.getId(), c.getVersionId(), people.get(c.getUserId()), c.getBody(),
                new double[] {c.getX(), c.getY(), c.getZ()}, c.getResolvedAt() != null, c.getCreatedAt(),
                change && found.can(ArchRole.COMMENT), change);
    }

    private static AttachmentResponse response(ArchAttachment a, Access found, Map<UUID, UserSummary> people) {
        return new AttachmentResponse(a.getId(), a.getKind(), a.getName(), a.getSizeBytes(),
                a.getUserId() == null ? null : people.get(a.getUserId()), a.getCreatedAt(),
                "/api/architecture/projects/" + a.getProjectId() + "/attachments/" + a.getId() + "/file", found.can(ArchRole.EDIT));
    }

    private static double[] point(List<Double> values) {
        double[] point = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = values.get(i);
            if (!Double.isFinite(v) || Math.abs(v) > MAX_COORDINATE) throw ArchitectureService.bad("Ponto fora da maquete.");
            point[i] = v;
        }
        return point;
    }

    private static boolean isPdf(byte[] bytes) {
        return bytes.length > 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
    }

    /** Nome mostrado: sem caminho nem caracteres de controle; vazio vira um nome padrão. */
    private static String cleanName(String name, boolean pdf) {
        String clean = name == null ? "" : name.replaceAll(".*[\\\\/]", "").replaceAll("\\p{Cntrl}", "").trim();
        if (clean.isEmpty()) clean = pdf ? "Planta.pdf" : "Imagem.jpg";
        return clean.length() > 200 ? clean.substring(0, 200) : clean;
    }

    private static BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN,
                "Só quem escreveu ou quem pode editar o projeto faz isso.");
    }
}
