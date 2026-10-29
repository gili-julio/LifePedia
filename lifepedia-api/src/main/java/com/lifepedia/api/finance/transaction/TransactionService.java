package com.lifepedia.api.finance.transaction;

import com.lifepedia.api.achievement.Achievement;
import com.lifepedia.api.achievement.AchievementProgress;
import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.category.Category;
import com.lifepedia.api.finance.category.CategoryService;
import com.lifepedia.api.finance.group.GroupFinanceService;
import com.lifepedia.api.finance.Installments;
import com.lifepedia.api.finance.Installments.Installment;
import com.lifepedia.api.finance.Installments.MoveRequest;
import com.lifepedia.api.finance.Installments.MoveResult;
import com.lifepedia.api.finance.group.GroupFinanceSync;
import com.lifepedia.api.finance.transaction.TransactionDtos.TransactionRequest;
import com.lifepedia.api.finance.transaction.TransactionDtos.TransactionResponse;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.PageResponse;
import com.lifepedia.api.shared.Paging;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final CategoryService categoryService;
    private final GroupFinanceService groupFinance;
    private final GroupFinanceSync groupSync;
    private final ApplicationEventPublisher events;

    public TransactionService(TransactionRepository repository, CategoryService categoryService,
                              GroupFinanceService groupFinance, GroupFinanceSync groupSync,
                              ApplicationEventPublisher events) {
        this.repository = repository;
        this.categoryService = categoryService;
        this.groupFinance = groupFinance;
        this.groupSync = groupSync;
        this.events = events;
    }

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "date", "t.date",
            "description", "lower(t.description)",
            "category", "lower(c.name)",
            "amount", "t.amount");

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> page(UUID userId, YearMonth month, EntryType type,
                                                  int page, int size, String sort) {
        Pageable pageable = Paging.pageable(page, size, sort, SORT_FIELDS, "date,desc", "t.createdAt", "t.id");
        return PageResponse.of(repository.page(userId, month, type, pageable),
                items -> items.stream().map(TransactionResponse::from).toList());
    }

    /** Sem paginação: {@code from} a {@code to}, opcionalmente por tipo e descrição (sem diferenciar maiúsculas). */
    @Transactional(readOnly = true)
    public List<TransactionResponse> search(UUID userId, YearMonth from, YearMonth to, EntryType type, String description) {
        if (to.isBefore(from)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Período inválido.");
        }
        String normalized = description == null || description.isBlank() ? null : normalize(description);
        return repository.search(userId, from, to, type, normalized).stream().map(TransactionResponse::from).toList();
    }

    /** Nomes que o usuário já usa (últimos 12 meses), para padronizar a importação. */
    @Transactional(readOnly = true)
    public List<String> descriptions(UUID userId) {
        return repository.topDescriptions(userId, YearMonth.now().minusMonths(11), PageRequest.of(0, 200));
    }

    /** Exclui ocasionais do usuário; todos precisam existir. */
    @Transactional
    public void deleteAll(UUID userId, Collection<UUID> ids) {
        Set<UUID> unique = new HashSet<>(ids);
        List<Transaction> found = repository.findByUserIdAndIdIn(userId, unique);
        if (found.size() != unique.size()) {
            throw new NotFoundException("Lançamento a remover não encontrado.");
        }
        repository.deleteAll(found);
    }

    public static String normalize(String description) {
        return description.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public TransactionResponse create(UUID userId, TransactionRequest request) {
        categoryService.requireUsable(userId, request.categoryId(), request.type());
        requireValidMonth(request);
        Transaction transaction = repository.save(newTransaction(userId, request));
        events.publishEvent(new AchievementProgress(userId, EnumSet.of(Achievement.TRANSACTIONS)));
        groupFinance.shareNew(userId, request.groupIds(), request.shareMode(), transaction);
        return TransactionResponse.from(transaction);
    }

    /** Tudo ou nada: um item inválido cancela o lote. */
    @Transactional
    public List<TransactionResponse> createBatch(UUID userId, List<TransactionRequest> requests) {
        Map<UUID, Category> categories = categoryService.mapByIdFor(userId);
        List<Transaction> transactions = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            TransactionRequest request = requests.get(i);
            Category category = categories.get(request.categoryId());
            try {
                if (category == null) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "Categoria inválida.");
                }
                CategoryService.requireMatchingType(category, request.type());
                requireValidMonth(request);
            } catch (BusinessException e) {
                throw new BusinessException(e.getStatus(), "Linha " + (i + 1) + ": " + e.getMessage());
            }
            transactions.add(newTransaction(userId, request));
        }
        events.publishEvent(new AchievementProgress(userId, EnumSet.of(Achievement.TRANSACTIONS)));
        return repository.saveAll(transactions).stream().map(TransactionResponse::from).toList();
    }

    @Transactional
    public TransactionResponse update(UUID userId, UUID id, TransactionRequest request) {
        Transaction transaction = find(userId, id);
        categoryService.requireUsable(userId, request.categoryId(), request.type());
        requireValidMonth(request);
        apply(transaction, request);
        groupSync.transactionChanged(transaction);
        return TransactionResponse.from(transaction);
    }

    /** Adianta ou adia o mês da parcela; com {@code following}, as próximas parcelas da compra vão junto. */
    @Transactional
    public MoveResult moveMonth(UUID userId, UUID id, MoveRequest request) {
        Transaction transaction = find(userId, id);
        long months = ChronoUnit.MONTHS.between(transaction.getMonth(), request.month());
        List<Transaction> moving = new ArrayList<>(List.of(transaction));
        if (Boolean.TRUE.equals(request.following())) {
            Installment installment = Installments.require(transaction.getDescription());
            repository.findByUserIdAndDescriptionStartingWithIgnoreCase(userId, installment.base()).stream()
                    .filter(t -> installment.isFollowedBy(t.getDescription()))
                    .forEach(moving::add);
        }
        moving.forEach(t -> t.setMonth(Installments.shift(t.getMonth(), months, t.getDate(), t.getDescription())));
        // A sincronização limpa o contexto do JPA: todos os meses mudam antes dela.
        moving.forEach(groupSync::transactionChanged);
        return new MoveResult(moving.size());
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repository.delete(find(userId, id));
    }

    private Transaction find(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Lançamento não encontrado."));
    }

    private static void requireValidMonth(TransactionRequest request) {
        if (request.effectiveMonth().isBefore(YearMonth.from(request.date()))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O mês do lançamento não pode ser anterior à data.");
        }
    }

    /** A descrição original só é gravada na criação. */
    private static Transaction newTransaction(UUID userId, TransactionRequest request) {
        Transaction transaction = new Transaction(userId);
        apply(transaction, request);
        String original = request.originalDescription();
        transaction.setOriginalDescription(original == null || original.isBlank() ? null : original.trim());
        return transaction;
    }

    private static void apply(Transaction transaction, TransactionRequest request) {
        transaction.setDescription(request.description().trim());
        transaction.setType(request.type());
        transaction.setCategoryId(request.categoryId());
        transaction.setAmount(request.amount());
        transaction.setDate(request.date());
        transaction.setMonth(request.effectiveMonth());
        transaction.setNotes(request.notes());
    }
}
