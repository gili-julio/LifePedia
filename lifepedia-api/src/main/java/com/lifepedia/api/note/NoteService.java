package com.lifepedia.api.note;

import com.lifepedia.api.attendance.CommitmentLinks;
import com.lifepedia.api.group.GroupDeletedEvent;
import com.lifepedia.api.note.NoteActivity.Action;
import com.lifepedia.api.note.NoteDtos.Activity;
import com.lifepedia.api.note.NoteDtos.ImageResponse;
import com.lifepedia.api.note.NoteDtos.Labels;
import com.lifepedia.api.note.NoteDtos.LinkOptions;
import com.lifepedia.api.note.NoteDtos.LinkedCommitment;
import com.lifepedia.api.note.NoteDtos.LinkedTask;
import com.lifepedia.api.note.NoteDtos.NoteData;
import com.lifepedia.api.note.NoteDtos.NoteRef;
import com.lifepedia.api.note.NoteDtos.NoteRequest;
import com.lifepedia.api.note.NoteDtos.NoteResponse;
import com.lifepedia.api.note.NoteDtos.NoteSummary;
import com.lifepedia.api.note.NoteDtos.Status;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.PageResponse;
import com.lifepedia.api.shared.Paging;
import com.lifepedia.api.shared.image.ImageFile;
import com.lifepedia.api.task.TaskLinks;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Notas de um dono (pessoa ou grupo): listagem, edição com versão, lixeira, fotos, ligações e atividade. */
@Service
public class NoteService {

    static final int MAX_CONTENT = 200_000;
    static final int MAX_NOTES = 2000;
    static final int MAX_IMAGES = 30;
    static final Duration TRASH_DAYS = Duration.ofDays(30);
    /** Edições da mesma pessoa dentro desse tempo viram uma linha só na atividade. */
    private static final Duration MERGE_EDITS = Duration.ofMinutes(30);
    /** Foto recém-enviada ainda pode não estar no texto salvo. */
    private static final Duration ORPHAN_GRACE = Duration.ofHours(1);
    private static final int EXCERPT = 160;
    private static final Map<String, String> SORT = Map.of(
            "updatedAt", "n.updatedAt",
            "createdAt", "n.createdAt",
            "title", "lower(n.title)");

    private final NoteRepository notes;
    private final NoteImageRepository images;
    private final NoteActivityRepository activities;
    private final NoteLabelService labels;
    private final NoteImageStore store;
    private final TaskLinks tasks;
    private final CommitmentLinks commitments;
    private final UserRepository users;
    private final Clock clock;

    public NoteService(NoteRepository notes, NoteImageRepository images, NoteActivityRepository activities,
                       NoteLabelService labels, NoteImageStore store, TaskLinks tasks, CommitmentLinks commitments,
                       UserRepository users, Clock clock) {
        this.notes = notes;
        this.images = images;
        this.activities = activities;
        this.labels = labels;
        this.store = store;
        this.tasks = tasks;
        this.commitments = commitments;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<NoteSummary> list(NoteScope scope, Status status, String q, UUID folderId, UUID tagId,
                                          UUID taskId, UUID commitmentId, int page, int size, String sort) {
        NoteOwner owner = scope.owner();
        if (status == Status.TEMPLATE && owner.isGroup()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Modelos ficam só nas suas notas.");
        }
        Boolean template = switch (status) {
            case TEMPLATE -> true;
            case TRASH -> null;
            default -> false;
        };
        Boolean archived = switch (status) {
            case ACTIVE -> false;
            case ARCHIVED -> true;
            default -> null;
        };
        String like = q == null || q.isBlank() ? null : "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        Page<Note> found = notes.search(owner.userId(), owner.groupId(), template, status == Status.TRASH, archived,
                folderId, tagId, taskId, commitmentId, like,
                Paging.pageable(page, size, sort, SORT, "updatedAt,desc", "n.id"));
        return PageResponse.of(found, list -> {
            Map<UUID, User> people = usersOf(list.stream().map(Note::getEditedBy).toList());
            return list.stream().map(n -> new NoteSummary(n.getId(), n.getTitle(), excerpt(n.getContent()), n.getIcon(),
                    n.getColor(), n.getFolderId(), List.copyOf(n.getTagIds()), n.getPinnedAt() != null, n.getArchivedAt(),
                    n.getDeletedAt(), n.getUpdatedAt(), summary(people.get(n.getEditedBy())), scope.canDelete(n))).toList();
        });
    }

    /** Notas em uso do dono, para os links {@code [[Nome]]}. */
    @Transactional(readOnly = true)
    public List<NoteRef> refs(NoteOwner owner) {
        return notes.refs(owner.userId(), owner.groupId()).stream().map(NoteService::ref).toList();
    }

    @Transactional(readOnly = true)
    public NoteResponse get(NoteScope scope, UUID id) {
        return response(scope, find(scope.owner(), id));
    }

    @Transactional
    public NoteResponse create(NoteScope scope, NoteRequest request) {
        NoteOwner owner = scope.owner();
        boolean template = Boolean.TRUE.equals(request.template());
        if (template && owner.isGroup()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Modelos ficam só nas suas notas.");
        }
        if (notes.countOwned(owner.userId(), owner.groupId()) >= MAX_NOTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_NOTES + " notas.");
        }
        Note note = new Note(owner, scope.userId(), template);
        apply(scope, note, request);
        notes.saveAndFlush(note);
        activities.save(new NoteActivity(note.getId(), scope.userId(), Action.CREATED));
        return response(scope, note);
    }

    /** Grava a nota inteira; {@code version} diferente da atual → 409 (alguém salvou antes). */
    @Transactional
    public NoteResponse update(NoteScope scope, UUID id, NoteRequest request) {
        Note note = notes.lockById(id).filter(n -> n.ownedBy(scope.owner()))
                .orElseThrow(() -> new NotFoundException("Nota não encontrada."));
        if (request.version() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Informe a versão da nota.");
        }
        if (note.getDeletedAt() != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Restaure a nota antes de editar.");
        }
        if (request.version() != note.getVersion()) {
            throw new BusinessException(HttpStatus.CONFLICT, "A nota foi alterada em outro lugar. Recarregue para ver a versão nova.");
        }
        apply(scope, note, request);
        notes.saveAndFlush(note);
        removeOrphanImages(note);
        logEdit(note.getId(), scope.userId());
        return response(scope, note);
    }

    @Transactional
    public void pin(NoteScope scope, UUID id, boolean pinned) {
        Note note = find(scope.owner(), id);
        if (note.getDeletedAt() != null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Nota na lixeira.");
        note.pin(pinned);
    }

    @Transactional
    public void archive(NoteScope scope, UUID id, boolean archived) {
        Note note = find(scope.owner(), id);
        if (note.getDeletedAt() != null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Nota na lixeira.");
        if ((note.getArchivedAt() != null) == archived) return;
        note.archive(archived);
        activities.save(new NoteActivity(id, scope.userId(), archived ? Action.ARCHIVED : Action.UNARCHIVED));
    }

    /** Vai para a lixeira (some depois de {@link #TRASH_DAYS}). */
    @Transactional
    public void trash(NoteScope scope, UUID id) {
        Note note = deletable(scope, id);
        if (note.getDeletedAt() != null) return;
        note.trash(true);
        note.pin(false);
        activities.save(new NoteActivity(id, scope.userId(), Action.TRASHED));
    }

    @Transactional
    public void restore(NoteScope scope, UUID id) {
        Note note = deletable(scope, id);
        if (note.getDeletedAt() == null) return;
        note.trash(false);
        activities.save(new NoteActivity(id, scope.userId(), Action.RESTORED));
    }

    /** Apaga de vez (só da lixeira), com as fotos. */
    @Transactional
    public void deletePermanently(NoteScope scope, UUID id) {
        Note note = deletable(scope, id);
        if (note.getDeletedAt() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mova para a lixeira antes de apagar de vez.");
        }
        purge(List.of(note));
    }

    @Transactional(readOnly = true)
    public List<Activity> activity(NoteScope scope, UUID id) {
        find(scope.owner(), id);
        List<NoteActivity> list = activities.findTop30ByNoteIdOrderByUpdatedAtDesc(id);
        Map<UUID, User> people = usersOf(list.stream().map(NoteActivity::getUserId).toList());
        return list.stream()
                .map(a -> new Activity(a.getId(), a.getAction(), summary(people.get(a.getUserId())), a.getUpdatedAt()))
                .toList();
    }

    /** Foto nova na nota; devolve a URL para pôr no markdown. */
    @Transactional
    public ImageResponse addImage(NoteScope scope, UUID id, byte[] bytes) {
        Note note = find(scope.owner(), id);
        if (note.getDeletedAt() != null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Nota na lixeira.");
        if (images.countByNoteId(id) >= MAX_IMAGES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_IMAGES + " fotos por nota.");
        }
        String key = store.save(bytes);
        images.save(new NoteImage(id, key));
        String base = scope.owner().isGroup() ? "/api/groups/" + note.getGroupId() + "/notes/" : "/api/notes/";
        return new ImageResponse(base + id + "/images/" + key);
    }

    @Transactional(readOnly = true)
    public ImageFile image(NoteScope scope, UUID id, String key) {
        find(scope.owner(), id);
        images.findByNoteIdAndImageKey(id, key).orElseThrow(() -> new NotFoundException("Imagem não encontrada."));
        return store.file(key);
    }

    @Transactional(readOnly = true)
    public LinkOptions linkOptions(NoteScope scope) {
        NoteOwner owner = scope.owner();
        List<LinkedTask> open = tasks.openOwned(scope.userId(), owner.groupId()).stream()
                .map(t -> new LinkedTask(t.id(), t.title())).toList();
        List<LinkedCommitment> own = owner.isGroup() ? List.of()
                : commitments.all(scope.userId()).stream().map(c -> new LinkedCommitment(c.id(), c.name())).toList();
        return new LinkOptions(open, own);
    }

    /** Notas pessoais para o arquivo da conta (só registro). */
    @Transactional(readOnly = true)
    public List<NoteData> export(UUID userId) {
        NoteOwner owner = NoteOwner.personal(userId);
        Labels own = labels.labels(owner);
        Map<UUID, String> folderNames = own.folders().stream()
                .collect(Collectors.toMap(NoteDtos.Folder::id, NoteDtos.Folder::name));
        Map<UUID, String> tagNames = own.tags().stream()
                .collect(Collectors.toMap(NoteDtos.Tag::id, NoteDtos.Tag::name));
        return notes.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(n -> new NoteData(n.getTitle(), n.getContent(), folderNames.get(n.getFolderId()),
                        n.getTagIds().stream().map(tagNames::get).filter(Objects::nonNull).sorted().toList(),
                        n.getIcon(), n.getColor(), n.isTemplate(), n.getPinnedAt() != null, n.getArchivedAt() != null,
                        n.getDeletedAt() != null, n.getCreatedAt(), n.getUpdatedAt()))
                .toList();
    }

    /** Apaga de vez o que está na lixeira há mais de {@link #TRASH_DAYS}. */
    @Transactional
    public int purgeTrash(OffsetDateTime now) {
        List<Note> old = notes.findByDeletedAtBefore(now.minus(TRASH_DAYS));
        purge(old);
        return old.size();
    }

    /** Conta excluída: o banco apaga as notas pessoais; aqui só as fotos. */
    public void deleteUserImagesAfterCommit(UUID userId) {
        images.keysOfUser(userId).forEach(store::deleteAfterCommit);
    }

    /** O banco apaga as notas do grupo em cascata; aqui só as fotos. */
    @EventListener
    public void onGroupDeleted(GroupDeletedEvent event) {
        images.keysOfGroup(event.groupId()).forEach(store::deleteAfterCommit);
    }

    /** Começo do texto sem marcação de markdown. */
    static String excerpt(String markdown) {
        String text = markdown
                .replaceAll("(?m)^\\s*```.*$", " ")
                .replaceAll("(?m)^\\s*\\|?[\\s:|-]*-[\\s:|-]*\\|?\\s*$", " ")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[\\[([^\\]]+)]]", "$1")
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s*[-*+] \\[[ xX]]\\s*", "")
                .replaceAll("(?m)^\\s*(#{1,6}|>|[-*+]|\\d+\\.)\\s+", "")
                .replaceAll("[*_~`|]", "")
                .replaceAll("(?m)^\\s*-{3,}\\s*$", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() <= EXCERPT ? text : text.substring(0, EXCERPT).trim() + "…";
    }

    private void apply(NoteScope scope, Note note, NoteRequest request) {
        NoteOwner owner = scope.owner();
        Set<UUID> tagIds = request.tagIds() == null ? Set.of() : new HashSet<>(request.tagIds());
        labels.requireFolder(owner, request.folderId());
        labels.requireTags(owner, tagIds);
        if (request.taskId() != null && tasks.owned(scope.userId(), owner.groupId(), request.taskId()).isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Tarefa inválida.");
        }
        if (request.commitmentId() != null
                && (owner.isGroup() || commitments.personal(scope.userId(), request.commitmentId()).isEmpty())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Compromisso inválido.");
        }
        String icon = request.icon() == null || request.icon().isBlank() ? null : request.icon().trim();
        String color = request.color() == null ? null : request.color().toLowerCase(Locale.ROOT);
        note.edit(scope.userId(), request.title().trim(), request.content(), request.folderId(), tagIds, icon, color,
                request.taskId(), request.commitmentId());
    }

    private NoteResponse response(NoteScope scope, Note note) {
        NoteOwner owner = scope.owner();
        Map<UUID, User> people = usersOf(Stream.of(note.getCreatedBy(), note.getEditedBy()).toList());
        LinkedTask task = note.getTaskId() == null ? null : tasks.owned(scope.userId(), owner.groupId(), note.getTaskId())
                .map(t -> new LinkedTask(t.id(), t.title())).orElse(null);
        LinkedCommitment commitment = note.getCommitmentId() == null || owner.isGroup() ? null
                : commitments.personal(scope.userId(), note.getCommitmentId())
                        .map(c -> new LinkedCommitment(c.id(), c.name())).orElse(null);
        List<NoteRef> backlinks = note.isTemplate() ? List.of()
                : notes.citing(owner.userId(), owner.groupId(), note.getId(),
                        "%[[" + escapeLike(note.getTitle().toLowerCase(Locale.ROOT)) + "]]%").stream()
                        .map(NoteService::ref).toList();
        return new NoteResponse(note.getId(), note.getTitle(), note.getContent(), note.getIcon(), note.getColor(),
                note.getFolderId(), List.copyOf(note.getTagIds()), note.isTemplate(), note.getPinnedAt() != null,
                note.getArchivedAt(), note.getDeletedAt(), task, commitment, backlinks,
                summary(people.get(note.getCreatedBy())), summary(people.get(note.getEditedBy())), note.getCreatedAt(),
                note.getUpdatedAt(), note.getVersion(), scope.canDelete(note));
    }

    private Note find(NoteOwner owner, UUID id) {
        return notes.findById(id).filter(n -> n.ownedBy(owner))
                .orElseThrow(() -> new NotFoundException("Nota não encontrada."));
    }

    private Note deletable(NoteScope scope, UUID id) {
        Note note = find(scope.owner(), id);
        if (!scope.canDelete(note)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Só quem criou a nota ou um administrador pode fazer isso.");
        }
        return note;
    }

    private void purge(List<Note> list) {
        if (list.isEmpty()) return;
        images.findByNoteIdIn(list.stream().map(Note::getId).toList())
                .forEach(i -> store.deleteAfterCommit(i.getImageKey()));
        notes.deleteAll(list);
    }

    /** Fotos que saíram do texto (e não são recém-enviadas) são apagadas. */
    private void removeOrphanImages(Note note) {
        OffsetDateTime limit = OffsetDateTime.now(clock).minus(ORPHAN_GRACE);
        List<NoteImage> orphans = images.findByNoteId(note.getId()).stream()
                .filter(i -> !note.getContent().contains(i.getImageKey()) && i.getCreatedAt().isBefore(limit))
                .toList();
        orphans.forEach(i -> store.deleteAfterCommit(i.getImageKey()));
        images.deleteAll(orphans);
    }

    private void logEdit(UUID noteId, UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        NoteActivity last = activities.findFirstByNoteIdOrderByUpdatedAtDesc(noteId).orElse(null);
        if (last != null && last.getAction() == Action.EDITED && userId.equals(last.getUserId())
                && last.getUpdatedAt().isAfter(now.minus(MERGE_EDITS))) {
            activities.touch(last.getId(), now);
            return;
        }
        activities.save(new NoteActivity(noteId, userId, Action.EDITED));
    }

    private Map<UUID, User> usersOf(Collection<UUID> ids) {
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return Map.of();
        return users.findAllById(distinct).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static UserSummary summary(User user) {
        return user == null ? null : UserSummary.from(user);
    }

    private static NoteRef ref(Object[] row) {
        return new NoteRef((UUID) row[0], (String) row[1], (String) row[2]);
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
