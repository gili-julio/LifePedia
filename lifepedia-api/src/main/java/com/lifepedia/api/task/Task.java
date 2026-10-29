package com.lifepedia.api.task;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Tarefa (pessoal ou de grupo), com dia, horário e regra de repetição opcionais. */
@Entity
@Table(name = "tasks")
public class Task extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "list_id")
    private UUID listId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 10000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Priority priority = Priority.NONE;

    /** Dia (sem repetição), início da regra ou, em "a partir da conclusão", a próxima ocorrência. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /** Lembrete N minutos antes do horário (0 = na hora); só com horário. */
    @Column(name = "reminder_minutes")
    private Integer reminderMinutes;

    /** Fixo do financeiro ligado (só pessoal): ao concluir, o app oferece registrar o valor do mês. */
    @Column(name = "recurring_entry_id")
    private UUID recurringEntryId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean habit;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_unit", length = 5)
    private Recurrence.Unit repeatUnit;

    @Column(name = "repeat_interval")
    private Integer repeatInterval;

    @Column(name = "repeat_weekdays")
    private Short repeatWeekdays;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_month_mode", length = 12)
    private Recurrence.MonthMode repeatMonthMode;

    @Column(name = "repeat_from_completion", nullable = false)
    private boolean repeatFromCompletion;

    @Column(name = "repeat_until")
    private LocalDate repeatUntil;

    @Column(name = "repeat_count")
    private Integer repeatCount;

    /** Sem repetição: concluída. "A partir da conclusão": série encerrada. */
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /** Responsáveis fixos (só em grupo; sem rodízio nem "todos"). */
    @ElementCollection
    @CollectionTable(name = "task_assignees", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "user_id")
    private Set<UUID> assignees = new HashSet<>();

    /** Todos os membros do grupo, inclusive quem entrar depois. */
    @Column(name = "all_members", nullable = false)
    private boolean allMembers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Completion completion = Completion.ANY;

    @Column(name = "rotation_start", nullable = false)
    private int rotationStart;

    /** Rodízio em ordem fixa (só em grupo, com repetição). */
    @ElementCollection
    @CollectionTable(name = "task_rotation", joinColumns = @JoinColumn(name = "task_id"))
    @OrderColumn(name = "position")
    @Column(name = "user_id")
    private List<UUID> rotation = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "task_tag_links", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();

    protected Task() {
    }

    public Task(TaskOwner owner, UUID createdBy, int position) {
        this.userId = owner.userId();
        this.groupId = owner.groupId();
        this.createdBy = createdBy;
        this.position = position;
    }

    public boolean ownedBy(TaskOwner owner) {
        return owner.owns(userId, groupId);
    }

    public TaskOwner owner() {
        return new TaskOwner(userId, groupId);
    }

    public void setDetails(String title, String notes, Priority priority, UUID listId, boolean habit) {
        this.title = title;
        this.notes = notes;
        this.priority = priority;
        this.listId = listId;
        this.habit = habit;
    }

    /** Sem horário, o lembrete sai junto. */
    public void setSchedule(LocalDate dueDate, LocalTime dueTime, Integer durationMinutes) {
        this.dueDate = dueDate;
        this.dueTime = dueTime;
        this.durationMinutes = durationMinutes;
        if (dueTime == null) reminderMinutes = null;
    }

    public void setReminder(Integer reminderMinutes) {
        this.reminderMinutes = dueTime == null ? null : reminderMinutes;
    }

    public Integer getReminderMinutes() {
        return reminderMinutes;
    }

    public UUID getRecurringEntryId() {
        return recurringEntryId;
    }

    public void setRecurringEntryId(UUID recurringEntryId) {
        this.recurringEntryId = recurringEntryId;
    }

    /** {@code rule} nula tira a repetição; o início da regra é o {@code dueDate}. */
    public void setRepeat(Recurrence rule) {
        repeatUnit = rule == null ? null : rule.unit();
        repeatInterval = rule == null ? null : rule.interval();
        repeatWeekdays = rule == null || rule.unit() != Recurrence.Unit.WEEK ? null : (short) rule.weekdays();
        repeatMonthMode = rule == null || rule.unit() != Recurrence.Unit.MONTH ? null : rule.monthMode();
        repeatFromCompletion = rule != null && rule.fromCompletion();
        repeatUntil = rule == null ? null : rule.until();
        repeatCount = rule == null ? null : rule.count();
    }

    /** Regra atual (nula sem repetição). */
    public Recurrence rule() {
        if (repeatUnit == null) return null;
        return new Recurrence(dueDate, repeatUnit, repeatInterval, repeatWeekdays == null ? 0 : repeatWeekdays,
                repeatMonthMode, repeatFromCompletion, repeatUntil, repeatCount);
    }

    public Recurrence.Unit getRepeatUnit() {
        return repeatUnit;
    }

    /** Uma ocorrência por vez, no {@code dueDate}: sem repetição ou "a partir da conclusão". */
    public boolean isSingleSlot() {
        return repeatUnit == null || repeatFromCompletion;
    }

    public void endRepeat(LocalDate until, Integer count) {
        this.repeatUntil = until;
        this.repeatCount = count;
    }

    /** Responsáveis fixos, "todos" ou rodízio (lista vazia = sem rodízio); "cada um" só com mais de um. */
    public void setAssignment(Set<UUID> assignees, boolean allMembers, List<UUID> rotation, Completion completion) {
        if (!this.assignees.equals(assignees)) {
            this.assignees.clear();
            this.assignees.addAll(assignees);
        }
        this.allMembers = allMembers;
        this.completion = (allMembers || assignees.size() > 1) && completion == Completion.EACH ? Completion.EACH : Completion.ANY;
        if (!getRotation().equals(rotation)) {
            this.rotation.clear();
            this.rotation.addAll(rotation);
        }
        if (rotation.isEmpty()) rotationStart = 0;
        else rotationStart = Math.floorMod(rotationStart, rotation.size());
    }

    /** Membros do rodízio, em ordem. */
    public List<UUID> getRotation() {
        return rotation.stream().filter(Objects::nonNull).toList();
    }

    /** Vez no rodízio da ocorrência de número {@code index} ("a partir da conclusão": sempre 0); nula sem rodízio. */
    public UUID turnAt(int index) {
        List<UUID> order = getRotation();
        if (order.isEmpty()) return null;
        return order.get(Math.floorMod(rotationStart + index, order.size()));
    }

    /** Passa a vez ({@code steps} negativo volta). */
    public void turn(int steps) {
        int size = getRotation().size();
        rotationStart = size == 0 ? 0 : Math.floorMod(rotationStart + steps, size);
    }

    public void setRotationStart(int rotationStart) {
        int size = getRotation().size();
        this.rotationStart = size == 0 ? 0 : Math.floorMod(rotationStart, size);
    }

    public int getRotationStart() {
        return rotationStart;
    }

    /** Quem saiu do grupo deixa de ser responsável e sai do rodízio (rodízio de uma pessoa vira responsável fixo). */
    public void removeMember(UUID userId) {
        if (assignees.remove(userId) && !allMembers && assignees.size() < 2) completion = Completion.ANY;
        List<UUID> order = new ArrayList<>(getRotation());
        int at = order.indexOf(userId);
        if (at < 0) return;
        order.remove(at);
        if (order.size() < 2) {
            setAssignment(order.isEmpty() ? Set.of() : Set.of(order.getFirst()), false, List.of(), Completion.ANY);
            return;
        }
        if (at < rotationStart) rotationStart--;
        rotation.clear();
        rotation.addAll(order);
        setRotationStart(rotationStart);
    }

    /** Tarefa pessoal vira do grupo: lista, etiquetas e fixo (pessoais) saem; quem levou fica responsável. */
    public void moveToGroup(UUID groupId, int position, UUID bringer) {
        this.userId = null;
        this.groupId = groupId;
        this.position = position;
        this.listId = null;
        this.recurringEntryId = null;
        this.tagIds.clear();
        setAssignment(Set.of(bringer), false, List.of(), Completion.ANY);
    }

    public Set<UUID> getAssignees() {
        return assignees;
    }

    public boolean isAllMembers() {
        return allMembers;
    }

    public Completion getCompletion() {
        return completion;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getListId() {
        return listId;
    }

    public String getTitle() {
        return title;
    }

    public String getNotes() {
        return notes;
    }

    public Priority getPriority() {
        return priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean isHabit() {
        return habit;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Set<UUID> getTagIds() {
        return tagIds;
    }

    public void setTagIds(Set<UUID> tagIds) {
        this.tagIds.clear();
        this.tagIds.addAll(tagIds);
    }
}
