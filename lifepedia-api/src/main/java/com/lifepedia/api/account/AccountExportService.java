package com.lifepedia.api.account;

import com.lifepedia.api.account.AccountDtos.AccountData;
import com.lifepedia.api.account.AccountDtos.AdjustmentData;
import com.lifepedia.api.account.AccountDtos.CategoryData;
import com.lifepedia.api.account.AccountDtos.GroupData;
import com.lifepedia.api.account.AccountDtos.GroupRecurringData;
import com.lifepedia.api.account.AccountDtos.GroupTransactionData;
import com.lifepedia.api.account.AccountDtos.PlanItemData;
import com.lifepedia.api.account.AccountDtos.Profile;
import com.lifepedia.api.account.AccountDtos.RecurringData;
import com.lifepedia.api.account.AccountDtos.TransactionData;
import com.lifepedia.api.attendance.AttendanceService;
import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.category.Category;
import com.lifepedia.api.finance.category.CategoryRepository;
import com.lifepedia.api.finance.group.GroupRecurringEntryRepository;
import com.lifepedia.api.finance.group.GroupTransaction;
import com.lifepedia.api.finance.group.GroupTransactionRepository;
import com.lifepedia.api.finance.recurring.RecurringAdjustment;
import com.lifepedia.api.finance.recurring.RecurringAdjustmentRepository;
import com.lifepedia.api.finance.recurring.RecurringEntryRepository;
import com.lifepedia.api.finance.transaction.TransactionRepository;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.group.UserGroup;
import com.lifepedia.api.group.UserGroupRepository;
import com.lifepedia.api.planning.PlanItem;
import com.lifepedia.api.planning.PlanItemRepository;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.task.Priority;
import com.lifepedia.api.task.TaskDtos.LabelData;
import com.lifepedia.api.task.TaskDtos.RepeatRule;
import com.lifepedia.api.task.TaskOccurrence;
import com.lifepedia.api.task.TaskTransferService;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Monta o arquivo de exportação (JSON importável) e a planilha só de leitura. */
@Service
public class AccountExportService {

    private static final int EXPORTS_PER_HOUR = 20;

    private final UserService users;
    private final CategoryRepository categories;
    private final RecurringEntryRepository recurring;
    private final RecurringAdjustmentRepository adjustments;
    private final TransactionRepository transactions;
    private final GroupMemberRepository members;
    private final UserGroupRepository groups;
    private final GroupTransactionRepository groupTransactions;
    private final GroupRecurringEntryRepository groupRecurring;
    private final PlanItemRepository planItems;
    private final TaskTransferService taskTransfer;
    private final AttendanceService attendance;
    private final RateLimiter rateLimiter;

    public AccountExportService(UserService users, CategoryRepository categories, RecurringEntryRepository recurring,
                                RecurringAdjustmentRepository adjustments, TransactionRepository transactions,
                                GroupMemberRepository members, UserGroupRepository groups,
                                GroupTransactionRepository groupTransactions, GroupRecurringEntryRepository groupRecurring,
                                PlanItemRepository planItems, TaskTransferService taskTransfer,
                                AttendanceService attendance, RateLimiter rateLimiter) {
        this.users = users;
        this.categories = categories;
        this.recurring = recurring;
        this.adjustments = adjustments;
        this.transactions = transactions;
        this.members = members;
        this.groups = groups;
        this.groupTransactions = groupTransactions;
        this.groupRecurring = groupRecurring;
        this.planItems = planItems;
        this.taskTransfer = taskTransfer;
        this.attendance = attendance;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public AccountData export(UUID userId) {
        acquire(userId);
        User user = users.find(userId);
        List<Category> cats = categories.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Category::getType).thenComparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<UUID, String> names = cats.stream().collect(Collectors.toMap(Category::getId, Category::getName));
        Map<UUID, List<RecurringAdjustment>> adjustmentsByEntry = adjustments.findByUserId(userId).stream()
                .sorted(Comparator.comparing(RecurringAdjustment::getMonth))
                .collect(Collectors.groupingBy(RecurringAdjustment::getRecurringEntryId));

        List<RecurringData> fixed = recurring.findByUserIdOrderByTypeAscAmountDesc(userId).stream()
                .map(r -> new RecurringData(ref(r.getId()), r.getDescription(), r.getType(), ref(r.getCategoryId()), r.getAmount(),
                        r.getDayOfMonth(), r.getStartMonth(), r.getEndMonth(), r.isActive(), r.getNotes(),
                        adjustmentsByEntry.getOrDefault(r.getId(), List.of()).stream()
                                .map(a -> new AdjustmentData(a.getMonth(), a.getAmount())).toList()))
                .toList();
        List<TransactionData> occasional = transactions.findByUserIdOrderByDateAscCreatedAtAsc(userId).stream()
                .map(t -> new TransactionData(t.getDescription(), t.getOriginalDescription(), t.getType(),
                        ref(t.getCategoryId()), t.getAmount(), t.getDate(), t.getMonth(), t.getNotes()))
                .toList();

        TaskTransferService.Exported own = taskTransfer.export(userId, AccountExportService::ref);

        return new AccountData(AccountDtos.FORMAT, OffsetDateTime.now(),
                new Profile(user.getName(), user.getUsername(), user.getEmail()),
                cats.stream().map(c -> new CategoryData(ref(c.getId()), c.getName(), c.getType(), c.getColor())).toList(),
                fixed, occasional, groups(userId, names), own.lists(), own.tags(), own.tasks(), attendance.export(userId));
    }

    /** Planilha com as mesmas informações, para abrir no Excel/Google Planilhas. */
    @Transactional(readOnly = true)
    public byte[] spreadsheet(UUID userId) {
        AccountData data = export(userId);
        Map<String, String> category = data.categories().stream()
                .collect(Collectors.toMap(CategoryData::ref, CategoryData::name));
        XlsxWriter xlsx = new XlsxWriter();

        XlsxWriter.Sheet sheet = xlsx.sheet("Lançamentos", "Data", "Mês", "Descrição", "Descrição original", "Tipo",
                "Categoria", "Valor", "Observações");
        data.transactions().forEach(t -> sheet.row(t.date(), t.month(), t.description(), t.originalDescription(),
                type(t.type()), category.get(t.category()), t.amount(), t.notes()));

        XlsxWriter.Sheet fixed = xlsx.sheet("Fixos", "Descrição", "Tipo", "Categoria", "Valor", "Dia", "Início", "Fim",
                "Ativo", "Observações");
        data.recurring().forEach(r -> fixed.row(r.description(), type(r.type()), category.get(r.category()), r.amount(),
                r.dayOfMonth(), r.startMonth(), r.endMonth(), Boolean.TRUE.equals(r.active()) ? "Sim" : "Não", r.notes()));

        XlsxWriter.Sheet adjusted = xlsx.sheet("Ajustes dos fixos", "Fixo", "Mês", "Valor no mês");
        data.recurring().forEach(r -> r.adjustments().forEach(a -> adjusted.row(r.description(), a.month(), a.amount())));

        Map<String, String> listName = data.taskLists().stream().collect(Collectors.toMap(LabelData::ref, LabelData::name));
        Map<String, String> tagName = data.taskTags().stream().collect(Collectors.toMap(LabelData::ref, LabelData::name));
        Map<String, String> fixedName = data.recurring().stream().collect(Collectors.toMap(RecurringData::ref, RecurringData::description));
        XlsxWriter.Sheet todo = xlsx.sheet("Tarefas", "Tarefa", "Lista", "Etiquetas", "Dia", "Horário", "Duração (min)",
                "Prioridade", "Repetição", "Hábito", "Lembrete (min antes)", "Fixo", "Concluída em", "Subtarefas", "Notas");
        data.tasks().forEach(t -> todo.row(t.title(), t.list() == null ? null : listName.get(t.list()),
                t.tags().stream().map(tagName::get).collect(Collectors.joining(", ")), t.dueDate(), t.dueTime(),
                t.durationMinutes(), priority(t.priority()), repeat(t.repeat()), Boolean.TRUE.equals(t.habit()) ? "Sim" : "Não",
                t.reminderMinutes(), t.fixed() == null ? null : fixedName.get(t.fixed()),
                t.completedAt() == null ? null : t.completedAt().toLocalDate(), String.join("; ", t.subtasks()), t.notes()));

        XlsxWriter.Sheet done = xlsx.sheet("Tarefas concluídas", "Tarefa", "Dia", "Concluída em");
        data.tasks().forEach(t -> t.occurrences().stream().filter(o -> o.status() == TaskOccurrence.Status.DONE)
                .forEach(o -> done.row(o.title() != null ? o.title() : t.title(), o.date(),
                        o.completedAt() == null ? null : o.completedAt().toLocalDate())));

        XlsxWriter.Sheet cats = xlsx.sheet("Categorias", "Nome", "Tipo", "Cor");
        data.categories().forEach(c -> cats.row(c.name(), type(c.type()), c.color()));

        XlsxWriter.Sheet inGroups = xlsx.sheet("Grupos", "Grupo", "Registro", "Descrição", "Tipo", "Categoria", "Valor",
                "Data", "Mês", "Sincronizado", "Observações");
        for (GroupData g : data.groups()) {
            g.transactions().forEach(t -> inGroups.row(g.name(), "Ocasional", t.description(), type(t.type()), t.category(),
                    t.amount(), t.date(), t.month(), t.synced() ? "Sim" : "Não", t.notes()));
            g.recurring().forEach(r -> inGroups.row(g.name(), "Fixo", r.description(), type(r.type()), r.category(),
                    r.amount(), null, r.startMonth(), r.synced() ? "Sim" : "Não", r.notes()));
            g.planItems().forEach(p -> inGroups.row(g.name(), "Planejamento", p.name(),
                    p.kind() == PlanItem.Kind.INVESTMENT ? "Investimento" : "Compra", null,
                    p.amount(), p.acquiredOn(), p.targetMonth(), null, p.notes()));
        }
        return xlsx.toBytes();
    }

    /** Nome do arquivo baixado, com a data. */
    public static String fileName(String extension) {
        return "lifepedia-" + DateTimeFormatter.ISO_LOCAL_DATE.format(OffsetDateTime.now()) + "." + extension;
    }

    private List<GroupData> groups(UUID userId, Map<UUID, String> categoryNames) {
        List<GroupMember> mine = members.findByUserId(userId);
        Map<UUID, UserGroup> byId = groups.findAllById(mine.stream().map(GroupMember::getGroupId).toList()).stream()
                .collect(Collectors.toMap(UserGroup::getId, Function.identity()));
        List<GroupData> result = new ArrayList<>();
        for (GroupMember m : mine) {
            UUID groupId = m.getGroupId();
            result.add(new GroupData(byId.get(groupId).getName(), m.getRole(),
                    groupTransactions.findByGroupIdAndUserId(groupId, userId).stream()
                            .sorted(Comparator.comparing(GroupTransaction::getDate))
                            .map(t -> new GroupTransactionData(t.getDescription(), t.getType(),
                                    categoryNames.get(t.getCategoryId()), t.getAmount(), t.getDate(), t.getMonth(),
                                    t.getNotes(), t.isSynced()))
                            .toList(),
                    groupRecurring.findByGroupIdAndUserId(groupId, userId).stream()
                            .map(r -> new GroupRecurringData(r.getDescription(), r.getType(),
                                    categoryNames.get(r.getCategoryId()), r.getAmount(), r.getDayOfMonth(),
                                    r.getStartMonth(), r.getEndMonth(), r.isActive(), r.getNotes(), r.isSynced()))
                            .toList(),
                    planItems.findByGroupIdAndUserId(groupId, userId).stream()
                            .map(p -> new PlanItemData(p.getName(), p.getKind(), p.getAmount(), p.getTargetMonth(),
                                    p.getInstallments(), p.isActive(), p.getNotes(), p.getUrl(), p.getAcquiredOn(),
                                    p.getAcquiredAmount()))
                            .toList()));
        }
        result.sort(Comparator.comparing(GroupData::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private void acquire(UUID userId) {
        if (!rateLimiter.tryAcquire("account-export:" + userId, EXPORTS_PER_HOUR, Duration.ofHours(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitas exportações seguidas. Tente de novo mais tarde.");
        }
    }

    private static String ref(UUID id) {
        return id.toString();
    }

    private static String priority(Priority priority) {
        return switch (priority) {
            case NONE -> null;
            case LOW -> "Baixa";
            case MEDIUM -> "Média";
            case HIGH -> "Alta";
        };
    }

    /** Ex.: "A cada 2 semana(s) (seg, qua) até 2026-12-31". */
    private static String repeat(RepeatRule rule) {
        if (rule == null) return null;
        String unit = switch (rule.unit()) {
            case DAY -> "dia(s)";
            case WEEK -> "semana(s)";
            case MONTH -> "mês(es)";
            case YEAR -> "ano(s)";
        };
        StringBuilder text = new StringBuilder("A cada " + rule.interval() + " " + unit);
        if (Boolean.TRUE.equals(rule.fromCompletion())) text.append(" após concluir");
        if (rule.weekdays() != null && !rule.weekdays().isEmpty()) {
            text.append(" (").append(rule.weekdays().stream().map(d -> WEEKDAYS[d - 1]).collect(Collectors.joining(", "))).append(")");
        }
        if (rule.until() != null) text.append(" até ").append(rule.until());
        if (rule.count() != null) text.append(", ").append(rule.count()).append(" vezes");
        return text.toString();
    }

    private static final String[] WEEKDAYS = {"seg", "ter", "qua", "qui", "sex", "sáb", "dom"};

    private static String type(EntryType type) {
        return type == EntryType.INCOME ? "Ganho" : "Gasto";
    }
}
