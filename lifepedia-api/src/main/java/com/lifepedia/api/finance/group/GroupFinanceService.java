package com.lifepedia.api.finance.group;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.category.Category;
import com.lifepedia.api.finance.category.CategoryRepository;
import com.lifepedia.api.finance.category.CategoryService;
import com.lifepedia.api.finance.group.GroupFinanceDtos.CategoryRef;
import com.lifepedia.api.finance.group.GroupFinanceDtos.GroupRecurringResponse;
import com.lifepedia.api.finance.group.GroupFinanceDtos.GroupTransactionResponse;
import com.lifepedia.api.finance.group.GroupFinanceDtos.Origin;
import com.lifepedia.api.finance.group.GroupFinanceDtos.Origins;
import com.lifepedia.api.finance.group.GroupFinanceDtos.ShareRequest;
import com.lifepedia.api.finance.group.GroupFinanceDtos.ShareResult;
import com.lifepedia.api.finance.recurring.RecurringEntry;
import com.lifepedia.api.finance.recurring.RecurringEntryDtos.RecurringEntryRequest;
import com.lifepedia.api.finance.recurring.RecurringEntryRepository;
import com.lifepedia.api.finance.transaction.Transaction;
import com.lifepedia.api.finance.transaction.TransactionDtos.TransactionRequest;
import com.lifepedia.api.finance.transaction.TransactionRepository;
import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.PageResponse;
import com.lifepedia.api.shared.Paging;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Itens financeiros dentro de grupos: levar (sincronizado ou cópia), cadastrar direto, listar, editar cópias e remover. */
@Service
public class GroupFinanceService {

    private static final Map<String, String> TRANSACTION_SORT = Map.of(
            "date", "g.date",
            "description", "lower(g.description)",
            "category", "lower(c.name)",
            "member", "lower(u.name)",
            "amount", "g.amount");

    private static final Map<String, String> RECURRING_SORT = Map.of(
            "description", "lower(r.description)",
            "category", "lower(c.name)",
            "member", "lower(u.name)",
            "day", "r.dayOfMonth",
            "start", "r.startMonth",
            "amount", "r.amount");

    private final GroupTransactionRepository groupTransactions;
    private final GroupRecurringEntryRepository groupRecurring;
    private final TransactionRepository transactions;
    private final RecurringEntryRepository recurring;
    private final CategoryRepository categories;
    private final CategoryService categoryService;
    private final UserRepository users;
    private final GroupAccess access;

    public GroupFinanceService(GroupTransactionRepository groupTransactions, GroupRecurringEntryRepository groupRecurring,
                               TransactionRepository transactions, RecurringEntryRepository recurring,
                               CategoryRepository categories, CategoryService categoryService, UserRepository users,
                               GroupAccess access) {
        this.groupTransactions = groupTransactions;
        this.groupRecurring = groupRecurring;
        this.transactions = transactions;
        this.recurring = recurring;
        this.categories = categories;
        this.categoryService = categoryService;
        this.users = users;
        this.access = access;
    }

    /** Leva itens pessoais para o grupo. Sincronizado repetido é ignorado; cópia sempre cria outra. */
    @Transactional
    public ShareResult share(UUID userId, UUID groupId, ShareRequest request) {
        access.requireMember(groupId, userId);
        Set<UUID> transactionIds = new LinkedHashSet<>(request.transactionIdsOrEmpty());
        Set<UUID> recurringIds = new LinkedHashSet<>(request.recurringIdsOrEmpty());
        if (transactionIds.isEmpty() && recurringIds.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha ao menos um item.");
        }
        List<Transaction> ownTransactions = transactions.findByUserIdAndIdIn(userId, transactionIds);
        List<RecurringEntry> ownRecurring = recurring.findByUserIdAndIdIn(userId, recurringIds);
        if (ownTransactions.size() != transactionIds.size() || ownRecurring.size() != recurringIds.size()) {
            throw new NotFoundException("Item não encontrado.");
        }
        int added = 0;
        for (Transaction t : ownTransactions) {
            if (addTransaction(groupId, t, request.mode())) added++;
        }
        for (RecurringEntry e : ownRecurring) {
            if (addRecurring(groupId, e, request.mode())) added++;
        }
        return new ShareResult(added, transactionIds.size() + recurringIds.size() - added);
    }

    /** Leva um ocasional recém-criado para os grupos escolhidos no cadastro. */
    @Transactional
    public void shareNew(UUID userId, Collection<UUID> groupIds, ShareMode mode, Transaction t) {
        for (UUID groupId : requireGroups(userId, groupIds)) addTransaction(groupId, t, modeOrSync(mode));
    }

    @Transactional
    public void shareNew(UUID userId, Collection<UUID> groupIds, ShareMode mode, RecurringEntry e) {
        for (UUID groupId : requireGroups(userId, groupIds)) addRecurring(groupId, e, modeOrSync(mode));
    }

    /** Lança uma despesa própria no grupo (cópia sem item pessoal), em parcelas mensais a partir do mês da data. */
    @Transactional
    public int addExpense(UUID userId, UUID groupId, String description, UUID categoryId, BigDecimal amount,
                          LocalDate date, int installments, String notes) {
        access.requireMember(groupId, userId);
        categoryService.requireUsable(userId, categoryId, EntryType.EXPENSE);
        BigDecimal part = amount.divide(BigDecimal.valueOf(installments), 2, RoundingMode.DOWN);
        if (part.signum() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Valor pequeno demais para tantas parcelas.");
        }
        BigDecimal last = amount.subtract(part.multiply(BigDecimal.valueOf(installments - 1L)));
        String name = description.length() > 108 ? description.substring(0, 108).trim() : description;
        YearMonth first = YearMonth.from(date);
        for (int i = 0; i < installments; i++) {
            GroupTransaction t = new GroupTransaction(groupId, userId);
            t.set(installments > 1 ? name + " (" + (i + 1) + "/" + installments + ")" : name, EntryType.EXPENSE,
                    categoryId, i == installments - 1 ? last : part, date, first.plusMonths(i), notes);
            groupTransactions.save(t);
        }
        return installments;
    }

    @Transactional(readOnly = true)
    public Origins origins(UUID userId, UUID groupId) {
        access.requireMember(groupId, userId);
        return new Origins(
                groupTransactions.findByGroupIdAndUserId(groupId, userId).stream()
                        .filter(g -> g.getOriginId() != null)
                        .map(g -> new Origin(g.getOriginId(), g.isSynced())).toList(),
                groupRecurring.findByGroupIdAndUserId(groupId, userId).stream()
                        .filter(g -> g.getOriginId() != null)
                        .map(g -> new Origin(g.getOriginId(), g.isSynced())).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupTransactionResponse> transactions(UUID userId, UUID groupId, YearMonth month, EntryType type,
                                                               int page, int size, String sort) {
        access.requireMember(groupId, userId);
        var pageable = Paging.pageable(page, size, sort, TRANSACTION_SORT, "date,desc", "g.createdAt", "g.id");
        return PageResponse.of(groupTransactions.page(groupId, month, type, pageable), items -> {
            Refs refs = refs(items.stream().map(GroupTransaction::getCategoryId).toList(),
                    items.stream().map(GroupTransaction::getUserId).toList());
            return items.stream().map(g -> new GroupTransactionResponse(g.getId(), g.getDescription(), g.getType(),
                    g.getAmount(), g.getDate(), g.getMonth(), g.getNotes(), refs.category(g.getCategoryId()),
                    refs.user(g.getUserId()), g.isSynced(), g.getUserId().equals(userId))).toList();
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupRecurringResponse> recurring(UUID userId, UUID groupId, EntryType type,
                                                          int page, int size, String sort) {
        access.requireMember(groupId, userId);
        var pageable = Paging.pageable(page, size, sort, RECURRING_SORT, "amount,desc", "r.id");
        return PageResponse.of(groupRecurring.page(groupId, type, pageable), items -> {
            Refs refs = refs(items.stream().map(GroupRecurringEntry::getCategoryId).toList(),
                    items.stream().map(GroupRecurringEntry::getUserId).toList());
            return items.stream().map(r -> new GroupRecurringResponse(r.getId(), r.getDescription(), r.getType(),
                    r.getAmount(), r.getDayOfMonth(), r.getStartMonth(), r.getEndMonth(), r.isActive(), r.getNotes(),
                    refs.category(r.getCategoryId()), refs.user(r.getUserId()), r.isSynced(),
                    r.getUserId().equals(userId))).toList();
        });
    }

    /** Cadastra um ocasional só do grupo (fica como cópia de quem cadastrou, sem item pessoal). */
    @Transactional
    public GroupTransactionResponse createTransaction(UUID userId, UUID groupId, TransactionRequest request) {
        access.requireMember(groupId, userId);
        GroupTransaction g = new GroupTransaction(groupId, userId);
        apply(g, userId, request);
        groupTransactions.save(g);
        Refs refs = refs(List.of(g.getCategoryId()), List.of(userId));
        return new GroupTransactionResponse(g.getId(), g.getDescription(), g.getType(), g.getAmount(), g.getDate(),
                g.getMonth(), g.getNotes(), refs.category(g.getCategoryId()), refs.user(userId), false, true);
    }

    @Transactional
    public GroupRecurringResponse createRecurring(UUID userId, UUID groupId, RecurringEntryRequest request) {
        access.requireMember(groupId, userId);
        GroupRecurringEntry r = new GroupRecurringEntry(groupId, userId);
        apply(r, userId, request, YearMonth.now());
        groupRecurring.save(r);
        Refs refs = refs(List.of(r.getCategoryId()), List.of(userId));
        return new GroupRecurringResponse(r.getId(), r.getDescription(), r.getType(), r.getAmount(), r.getDayOfMonth(),
                r.getStartMonth(), r.getEndMonth(), r.isActive(), r.getNotes(), refs.category(r.getCategoryId()),
                refs.user(userId), false, true);
    }

    /** Edita uma cópia (só quem levou; sincronizado se edita no pessoal). */
    @Transactional
    public void updateTransaction(UUID userId, UUID groupId, UUID id, TransactionRequest request) {
        access.requireMember(groupId, userId);
        GroupTransaction item = groupTransactions.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new NotFoundException("Lançamento não encontrado."));
        requireEditableCopy(item.getUserId(), item.isSynced(), userId);
        apply(item, userId, request);
    }

    @Transactional
    public void updateRecurring(UUID userId, UUID groupId, UUID id, RecurringEntryRequest request) {
        access.requireMember(groupId, userId);
        GroupRecurringEntry item = groupRecurring.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new NotFoundException("Lançamento fixo não encontrado."));
        requireEditableCopy(item.getUserId(), item.isSynced(), userId);
        apply(item, userId, request, item.getStartMonth());
    }

    private void apply(GroupTransaction item, UUID userId, TransactionRequest request) {
        categoryService.requireUsable(userId, request.categoryId(), request.type());
        if (request.effectiveMonth().isBefore(YearMonth.from(request.date()))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O mês do lançamento não pode ser anterior à data.");
        }
        item.set(request.description().trim(), request.type(), request.categoryId(), request.amount(), request.date(),
                request.effectiveMonth(), request.notes());
    }

    /** @param defaultStart início quando o pedido não traz {@code startMonth} */
    private void apply(GroupRecurringEntry item, UUID userId, RecurringEntryRequest request, YearMonth defaultStart) {
        categoryService.requireUsable(userId, request.categoryId(), request.type());
        YearMonth start = request.startMonth() != null ? request.startMonth() : defaultStart;
        if (request.endMonth() != null && request.endMonth().isBefore(start)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O mês final não pode ser anterior ao mês inicial.");
        }
        item.set(request.description().trim(), request.type(), request.categoryId(), request.amount(),
                request.dayOfMonth(), start, request.endMonth(), request.active() == null || request.active(),
                request.notes());
    }

    /** Tira do grupo (quem levou ou admin). Não mexe no item pessoal. */
    @Transactional
    public void removeTransaction(UUID userId, UUID groupId, UUID id) {
        GroupMember member = access.requireMember(groupId, userId);
        GroupTransaction item = groupTransactions.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new NotFoundException("Lançamento não encontrado."));
        requireCanRemove(member, item.getUserId());
        groupTransactions.delete(item);
    }

    @Transactional
    public void removeRecurring(UUID userId, UUID groupId, UUID id) {
        GroupMember member = access.requireMember(groupId, userId);
        GroupRecurringEntry item = groupRecurring.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new NotFoundException("Lançamento fixo não encontrado."));
        requireCanRemove(member, item.getUserId());
        groupRecurring.delete(item);
    }

    private boolean addTransaction(UUID groupId, Transaction t, ShareMode mode) {
        if (mode == ShareMode.SYNC && groupTransactions.existsByGroupIdAndSourceId(groupId, t.getId())) return false;
        groupTransactions.save(new GroupTransaction(groupId, t, mode));
        return true;
    }

    private boolean addRecurring(UUID groupId, RecurringEntry e, ShareMode mode) {
        if (mode == ShareMode.SYNC && groupRecurring.existsByGroupIdAndSourceId(groupId, e.getId())) return false;
        groupRecurring.save(new GroupRecurringEntry(groupId, e, mode));
        return true;
    }

    private Set<UUID> requireGroups(UUID userId, Collection<UUID> groupIds) {
        Set<UUID> ids = groupIds == null ? Set.of() : new HashSet<>(groupIds);
        ids.forEach(groupId -> access.requireMember(groupId, userId));
        return ids;
    }

    private static ShareMode modeOrSync(ShareMode mode) {
        return mode == null ? ShareMode.SYNC : mode;
    }

    private static void requireEditableCopy(UUID ownerId, boolean synced, UUID userId) {
        if (!ownerId.equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Só quem levou o item para o grupo pode editá-lo.");
        }
        if (synced) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Item sincronizado: edite o lançamento pessoal.");
        }
    }

    private static void requireCanRemove(GroupMember member, UUID ownerId) {
        if (!ownerId.equals(member.getUserId()) && !member.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Só quem levou o item ou um administrador pode removê-lo.");
        }
    }

    private Refs refs(Collection<UUID> categoryIds, Collection<UUID> userIds) {
        return new Refs(
                categories.findAllById(new HashSet<>(categoryIds)).stream()
                        .collect(Collectors.toMap(Category::getId, Function.identity())),
                users.findAllById(new HashSet<>(userIds)).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity())));
    }

    private record Refs(Map<UUID, Category> categories, Map<UUID, User> users) {

        CategoryRef category(UUID id) {
            Category c = categories.get(id);
            return new CategoryRef(c.getId(), c.getName(), c.getColor());
        }

        UserSummary user(UUID id) {
            return UserSummary.from(users.get(id));
        }
    }
}
