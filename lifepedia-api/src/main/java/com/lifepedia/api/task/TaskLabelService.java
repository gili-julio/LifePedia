package com.lifepedia.api.task;

import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.task.TaskDtos.Label;
import com.lifepedia.api.task.TaskDtos.LabelRequest;
import com.lifepedia.api.task.TaskDtos.Labels;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Listas e etiquetas de um dono (usuário ou grupo). Excluir não apaga tarefas: só as desliga. */
@Service
public class TaskLabelService {

    static final int MAX_LISTS = 50;
    static final int MAX_TAGS = 100;
    private static final int TAG_NAME_MAX = 40;

    private final TaskListRepository lists;
    private final TaskTagRepository tags;
    private final TaskRepository tasks;

    public TaskLabelService(TaskListRepository lists, TaskTagRepository tags, TaskRepository tasks) {
        this.lists = lists;
        this.tags = tags;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public Labels labels(TaskOwner owner) {
        List<TaskList> ownLists = lists.owned(owner.userId(), owner.groupId());
        List<TaskTag> ownTags = tags.owned(owner.userId(), owner.groupId());
        Map<UUID, Long> listCounts = counts(ownLists.isEmpty() ? List.of() : tasks.openByList(ids(ownLists)));
        Map<UUID, Long> tagCounts = counts(ownTags.isEmpty() ? List.of() : tasks.openByTag(tagIds(ownTags)));
        return new Labels(
                ownLists.stream().map(l -> new Label(l.getId(), l.getName(), l.getColor(), listCounts.getOrDefault(l.getId(), 0L))).toList(),
                ownTags.stream().map(t -> new Label(t.getId(), t.getName(), t.getColor(), tagCounts.getOrDefault(t.getId(), 0L))).toList());
    }

    @Transactional
    public Label createList(TaskOwner owner, LabelRequest request) {
        if (lists.owned(owner.userId(), owner.groupId()).size() >= MAX_LISTS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_LISTS + " listas.");
        }
        String name = request.name().trim();
        requireFreeListName(owner, name, null);
        TaskList list = lists.save(new TaskList(owner, name, request.color()));
        return new Label(list.getId(), list.getName(), list.getColor(), 0);
    }

    @Transactional
    public Label updateList(TaskOwner owner, UUID id, LabelRequest request) {
        TaskList list = list(owner, id);
        String name = request.name().trim();
        requireFreeListName(owner, name, id);
        list.set(name, request.color());
        return new Label(list.getId(), list.getName(), list.getColor(), 0);
    }

    @Transactional
    public void deleteList(TaskOwner owner, UUID id) {
        lists.delete(list(owner, id));
    }

    @Transactional
    public Label createTag(TaskOwner owner, LabelRequest request) {
        if (tags.owned(owner.userId(), owner.groupId()).size() >= MAX_TAGS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_TAGS + " etiquetas.");
        }
        String name = tagName(request);
        requireFreeTagName(owner, name, null);
        TaskTag tag = tags.save(new TaskTag(owner, name, request.color()));
        return new Label(tag.getId(), tag.getName(), tag.getColor(), 0);
    }

    @Transactional
    public Label updateTag(TaskOwner owner, UUID id, LabelRequest request) {
        TaskTag tag = tag(owner, id);
        String name = tagName(request);
        requireFreeTagName(owner, name, id);
        tag.set(name, request.color());
        return new Label(tag.getId(), tag.getName(), tag.getColor(), 0);
    }

    @Transactional
    public void deleteTag(TaskOwner owner, UUID id) {
        tags.delete(tag(owner, id));
    }

    /** Lista usável numa tarefa do dono (400 se não for). */
    void requireList(TaskOwner owner, UUID listId) {
        if (listId != null && lists.findById(listId).filter(l -> l.ownedBy(owner)).isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Lista inválida.");
        }
    }

    void requireTags(TaskOwner owner, Set<UUID> tagIds) {
        if (tagIds.isEmpty()) return;
        List<TaskTag> found = tags.findAllById(tagIds);
        if (found.size() != tagIds.size() || !found.stream().allMatch(t -> t.ownedBy(owner))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Etiqueta inválida.");
        }
    }

    private TaskList list(TaskOwner owner, UUID id) {
        return lists.findById(id).filter(l -> l.ownedBy(owner))
                .orElseThrow(() -> new NotFoundException("Lista não encontrada."));
    }

    private TaskTag tag(TaskOwner owner, UUID id) {
        return tags.findById(id).filter(t -> t.ownedBy(owner))
                .orElseThrow(() -> new NotFoundException("Etiqueta não encontrada."));
    }

    private void requireFreeListName(TaskOwner owner, String name, UUID exceptId) {
        if (lists.nameTaken(owner.userId(), owner.groupId(), name, exceptId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Já existe uma lista com esse nome.");
        }
    }

    private void requireFreeTagName(TaskOwner owner, String name, UUID exceptId) {
        if (tags.nameTaken(owner.userId(), owner.groupId(), name, exceptId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Já existe uma etiqueta com esse nome.");
        }
    }

    private static String tagName(LabelRequest request) {
        String name = request.name().trim();
        if (name.length() > TAG_NAME_MAX) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Etiqueta: máximo de " + TAG_NAME_MAX + " caracteres.");
        }
        return name;
    }

    private static Collection<UUID> ids(List<TaskList> all) {
        return all.stream().map(TaskList::getId).toList();
    }

    private static Collection<UUID> tagIds(List<TaskTag> all) {
        return all.stream().map(TaskTag::getId).toList();
    }

    private static Map<UUID, Long> counts(List<Object[]> rows) {
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : rows) result.put((UUID) row[0], (Long) row[1]);
        return result;
    }
}
