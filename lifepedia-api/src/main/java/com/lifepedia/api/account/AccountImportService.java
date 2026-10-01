package com.lifepedia.api.account;

import com.lifepedia.api.account.AccountDtos.AccountData;
import com.lifepedia.api.account.AccountDtos.AdjustmentData;
import com.lifepedia.api.account.AccountDtos.CategoryData;
import com.lifepedia.api.account.AccountDtos.Counts;
import com.lifepedia.api.account.AccountDtos.ImportMode;
import com.lifepedia.api.account.AccountDtos.ImportRequest;
import com.lifepedia.api.account.AccountDtos.ImportResult;
import com.lifepedia.api.account.AccountDtos.RecurringData;
import com.lifepedia.api.account.AccountDtos.TransactionData;
import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.category.Category;
import com.lifepedia.api.finance.category.CategoryRepository;
import com.lifepedia.api.finance.group.GroupRecurringEntryRepository;
import com.lifepedia.api.finance.group.GroupTransactionRepository;
import com.lifepedia.api.finance.recurring.RecurringAdjustment;
import com.lifepedia.api.finance.recurring.RecurringAdjustmentRepository;
import com.lifepedia.api.finance.recurring.RecurringEntry;
import com.lifepedia.api.finance.recurring.RecurringEntryRepository;
import com.lifepedia.api.finance.transaction.Transaction;
import com.lifepedia.api.finance.transaction.TransactionRepository;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.task.TaskTransferService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Importa o arquivo exportado para o financeiro e as tarefas pessoais (tudo ou nada). Categorias iguais (tipo + nome)
 * são reaproveitadas; no MERGE, item igual a um que já existe não duplica.
 */
@Service
public class AccountImportService {

    private static final int PREVIEWS_PER_HOUR = 60;
    private static final int IMPORTS_PER_HOUR = 10;

    private final CategoryRepository categories;
    private final RecurringEntryRepository recurring;
    private final RecurringAdjustmentRepository adjustments;
    private final TransactionRepository transactions;
    private final GroupTransactionRepository groupTransactions;
    private final GroupRecurringEntryRepository groupRecurring;
    private final TaskTransferService taskTransfer;
    private final RateLimiter rateLimiter;

    public AccountImportService(CategoryRepository categories, RecurringEntryRepository recurring,
                                RecurringAdjustmentRepository adjustments, TransactionRepository transactions,
                                GroupTransactionRepository groupTransactions, GroupRecurringEntryRepository groupRecurring,
                                TaskTransferService taskTransfer, RateLimiter rateLimiter) {
        this.categories = categories;
        this.recurring = recurring;
        this.adjustments = adjustments;
        this.transactions = transactions;
        this.groupTransactions = groupTransactions;
        this.groupRecurring = groupRecurring;
        this.taskTransfer = taskTransfer;
        this.rateLimiter = rateLimiter;
    }

    /** Com {@code dryRun} só calcula o resultado (prévia). */
    @Transactional
    public ImportResult importData(UUID userId, ImportRequest request) {
        boolean apply = !request.dryRunOrFalse();
        acquire(userId, apply);
        AccountData data = request.data();
        Map<String, CategoryData> byRef = validate(data);
        boolean replace = request.mode() == ImportMode.REPLACE;

        // Categorias atuais: iguais às do arquivo são reaproveitadas; no REPLACE, as outras sem uso em grupo saem.
        Set<EntryType> typesInFile = data.categories().stream().map(CategoryData::type).collect(Collectors.toSet());
        Set<String> keysInFile = data.categories().stream().map(c -> key(c.type(), c.name())).collect(Collectors.toSet());
        Map<String, UUID> categoryByKey = new HashMap<>();
        List<Category> dropped = new ArrayList<>();
        for (Category c : categories.findByUserId(userId)) {
            String key = key(c.getType(), c.getName());
            if (replace && !keysInFile.contains(key) && typesInFile.contains(c.getType()) && !usedInGroups(c.getId())) {
                dropped.add(c);
            } else {
                categoryByKey.put(key, c.getId());
            }
        }

        int removedTransactions = 0, removedRecurring = 0, detached = 0;
        if (replace) {
            removedTransactions = (int) transactions.countByUserId(userId);
            removedRecurring = (int) recurring.countByUserId(userId);
            detached = (int) (groupTransactions.countByUserIdAndSourceIdIsNotNull(userId)
                    + groupRecurring.countByUserIdAndSourceIdIsNotNull(userId));
            if (apply) {
                groupTransactions.detachSynced(userId);
                groupRecurring.detachSynced(userId);
                transactions.deleteAllOfUser(userId);
                recurring.deleteAllOfUser(userId);
                categories.deleteAll(dropped);
                categories.flush();
            }
        }

        // Ref do arquivo → categoria (existente ou nova)
        Map<String, UUID> categoryByRef = new HashMap<>();
        Set<UUID> created = new HashSet<>();
        Set<UUID> reused = new HashSet<>();
        for (CategoryData c : data.categories()) {
            String key = key(c.type(), c.name());
            UUID id = categoryByKey.get(key);
            if (id == null) {
                id = apply
                        ? categories.save(new Category(userId, c.name().trim(), c.type(), c.color().toLowerCase(Locale.ROOT))).getId()
                        : UUID.randomUUID();
                categoryByKey.put(key, id);
                created.add(id);
            } else if (!created.contains(id)) {
                reused.add(id);
            }
            categoryByRef.put(c.ref(), id);
        }

        RecurringOutcome fixed = importRecurring(userId, data.recurring(), categoryByRef, byRef, replace, apply);
        int[] occasional = importTransactions(userId, data.transactions(), categoryByRef, byRef, replace, apply);
        // Arquivo @1 não tem tarefas: elas ficam como estão.
        TaskTransferService.Outcome todo = data.tasks() == null ? new TaskTransferService.Outcome(0, 0, 0, 0)
                : taskTransfer.importTasks(userId, list(data.taskLists()), list(data.taskTags()), data.tasks(), replace,
                apply, fixed.idByRef);

        return new ImportResult(apply,
                new Counts(created.size(), occasional[0], fixed.created, fixed.adjustmentsCreated, todo.created(),
                        todo.labelsCreated()),
                new Counts(0, occasional[1], fixed.skipped, fixed.adjustmentsSkipped, todo.skipped(), 0),
                replace ? new Counts(dropped.size(), removedTransactions, removedRecurring, 0, todo.removed(), 0) : Counts.NONE,
                reused.size(), detached);
    }

    private RecurringOutcome importRecurring(UUID userId, List<RecurringData> list, Map<String, UUID> categoryByRef,
                                             Map<String, CategoryData> byRef, boolean replace, boolean apply) {
        Map<String, Deque<RecurringEntry>> existing = new HashMap<>();
        Map<UUID, Set<YearMonth>> adjusted = new HashMap<>();
        if (!replace) {
            Map<UUID, String> names = categoryNames(userId);
            for (RecurringEntry r : recurring.findByUserIdOrderByTypeAscAmountDesc(userId)) {
                existing.computeIfAbsent(recurringKey(r.getDescription(), r.getType(), names.get(r.getCategoryId()),
                        r.getAmount(), r.getStartMonth()), k -> new ArrayDeque<>()).add(r);
            }
            for (RecurringAdjustment a : adjustments.findByUserId(userId)) {
                adjusted.computeIfAbsent(a.getRecurringEntryId(), k -> new HashSet<>()).add(a.getMonth());
            }
        }
        RecurringOutcome out = new RecurringOutcome();
        List<RecurringAdjustment> newAdjustments = new ArrayList<>();
        for (RecurringData r : list) {
            Deque<RecurringEntry> same = existing.get(recurringKey(r.description(), r.type(),
                    byRef.get(r.category()).name(), r.amount(), r.startMonth()));
            RecurringEntry entry;
            if (same != null && !same.isEmpty()) {
                entry = same.poll();
                out.skipped++;
            } else {
                entry = new RecurringEntry(userId);
                entry.setDescription(r.description().trim());
                entry.setType(r.type());
                entry.setCategoryId(categoryByRef.get(r.category()));
                entry.setAmount(r.amount());
                entry.setDayOfMonth(r.dayOfMonth());
                entry.setStartMonth(r.startMonth());
                entry.setEndMonth(r.endMonth());
                entry.setActive(r.active() == null || r.active());
                entry.setNotes(blankToNull(r.notes()));
                if (apply) entry = recurring.save(entry);
                out.created++;
            }
            if (r.ref() != null) out.idByRef.put(r.ref(), entry.getId());
            Set<YearMonth> months = entry.getId() == null ? new HashSet<>()
                    : adjusted.computeIfAbsent(entry.getId(), k -> new HashSet<>());
            for (AdjustmentData a : r.adjustments() == null ? List.<AdjustmentData>of() : r.adjustments()) {
                boolean inPeriod = !a.month().isBefore(entry.getStartMonth())
                        && (entry.getEndMonth() == null || !a.month().isAfter(entry.getEndMonth()));
                if (!inPeriod || !months.add(a.month())) {
                    out.adjustmentsSkipped++;
                    continue;
                }
                if (apply) newAdjustments.add(new RecurringAdjustment(userId, entry.getId(), a.month(), a.amount()));
                out.adjustmentsCreated++;
            }
        }
        adjustments.saveAll(newAdjustments);
        return out;
    }

    /** @return [criados, iguais a existentes] */
    private int[] importTransactions(UUID userId, List<TransactionData> list, Map<String, UUID> categoryByRef,
                                     Map<String, CategoryData> byRef, boolean replace, boolean apply) {
        Map<String, Integer> existing = new HashMap<>();
        if (!replace) {
            Map<UUID, String> names = categoryNames(userId);
            for (Transaction t : transactions.findByUserIdOrderByDateAscCreatedAtAsc(userId)) {
                existing.merge(transactionKey(t.getDescription(), t.getType(), names.get(t.getCategoryId()), t.getAmount(),
                        t.getDate().toString(), t.getMonth()), 1, Integer::sum);
            }
        }
        int created = 0, skipped = 0;
        List<Transaction> batch = new ArrayList<>();
        for (TransactionData t : list) {
            String key = transactionKey(t.description(), t.type(), byRef.get(t.category()).name(), t.amount(),
                    t.date().toString(), t.month());
            Integer same = existing.get(key);
            if (same != null && same > 0) {
                existing.put(key, same - 1);
                skipped++;
                continue;
            }
            created++;
            if (!apply) continue;
            Transaction entity = new Transaction(userId);
            entity.setDescription(t.description().trim());
            entity.setOriginalDescription(blankToNull(t.originalDescription()));
            entity.setType(t.type());
            entity.setCategoryId(categoryByRef.get(t.category()));
            entity.setAmount(t.amount());
            entity.setDate(t.date());
            entity.setMonth(t.month());
            entity.setNotes(blankToNull(t.notes()));
            batch.add(entity);
        }
        transactions.saveAll(batch);
        return new int[]{created, skipped};
    }

    /** Versão, refs de categoria e regras que o Bean Validation não cobre. */
    private static Map<String, CategoryData> validate(AccountData data) {
        if (!AccountDtos.IMPORTABLE.contains(data.format())) {
            throw bad("Arquivo não reconhecido: use um arquivo exportado pelo LifePedia (\"" + AccountDtos.FORMAT + "\").");
        }
        Map<String, CategoryData> byRef = new HashMap<>();
        for (CategoryData c : data.categories()) {
            if (byRef.put(c.ref(), c) != null) throw bad("Categoria repetida no arquivo: " + c.name() + ".");
        }
        for (RecurringData r : data.recurring()) {
            requireCategory(byRef, r.category(), r.type(), r.description());
            if (r.endMonth() != null && r.endMonth().isBefore(r.startMonth())) {
                throw bad("Fixo \"" + r.description() + "\": o mês final é anterior ao inicial.");
            }
        }
        for (TransactionData t : data.transactions()) {
            requireCategory(byRef, t.category(), t.type(), t.description());
            if (t.month().isBefore(YearMonth.from(t.date()))) {
                throw bad("Lançamento \"" + t.description() + "\": o mês é anterior à data.");
            }
        }
        Set<String> fixedRefs = new HashSet<>();
        for (RecurringData r : data.recurring()) {
            if (r.ref() != null && !fixedRefs.add(r.ref())) throw bad("Fixo repetido no arquivo: " + r.description() + ".");
        }
        if (data.tasks() != null) {
            TaskTransferService.validate(list(data.taskLists()), list(data.taskTags()), data.tasks(), fixedRefs);
        }
        return byRef;
    }

    private static void requireCategory(Map<String, CategoryData> byRef, String ref, EntryType type, String item) {
        CategoryData c = byRef.get(ref);
        if (c == null) throw bad("\"" + item + "\" usa uma categoria que não está no arquivo.");
        if (c.type() != type) throw bad("\"" + item + "\" usa a categoria \"" + c.name() + "\", que é de outro tipo.");
    }

    private boolean usedInGroups(UUID categoryId) {
        return groupTransactions.countByCategoryId(categoryId) + groupRecurring.countByCategoryId(categoryId) > 0;
    }

    private Map<UUID, String> categoryNames(UUID userId) {
        return categories.findByUserId(userId).stream().collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private void acquire(UUID userId, boolean apply) {
        boolean ok = apply
                ? rateLimiter.tryAcquire("account-import:" + userId, IMPORTS_PER_HOUR, Duration.ofHours(1))
                : rateLimiter.tryAcquire("account-import-preview:" + userId, PREVIEWS_PER_HOUR, Duration.ofHours(1));
        if (!ok) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitas importações seguidas. Tente de novo mais tarde.");
    }

    private static String key(EntryType type, String name) {
        return type + ":" + normalize(name);
    }

    private static String recurringKey(String description, EntryType type, String category, BigDecimal amount,
                                       YearMonth start) {
        return String.join("|", normalize(description), type.name(), normalize(category), amount.stripTrailingZeros()
                .toPlainString(), start.toString());
    }

    private static String transactionKey(String description, EntryType type, String category, BigDecimal amount,
                                         String date, YearMonth month) {
        return String.join("|", normalize(description), type.name(), normalize(category), amount.stripTrailingZeros()
                .toPlainString(), date, month.toString());
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static BusinessException bad(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    private static <T> List<T> list(List<T> items) {
        return items == null ? List.of() : items;
    }

    private static final class RecurringOutcome {
        int created, skipped, adjustmentsCreated, adjustmentsSkipped;
        /** Ref do arquivo → fixo (criado ou igual a um existente; na prévia, nulo para os novos). */
        final Map<String, UUID> idByRef = new HashMap<>();
    }
}
