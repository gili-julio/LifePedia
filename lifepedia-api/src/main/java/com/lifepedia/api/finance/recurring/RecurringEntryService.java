package com.lifepedia.api.finance.recurring;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.category.CategoryService;
import com.lifepedia.api.finance.group.GroupFinanceService;
import com.lifepedia.api.finance.group.GroupFinanceSync;
import com.lifepedia.api.finance.recurring.RecurringEntryDtos.AdjustmentResponse;
import com.lifepedia.api.finance.recurring.RecurringEntryDtos.RecurringEntryRequest;
import com.lifepedia.api.finance.recurring.RecurringEntryDtos.RecurringEntryResponse;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.finance.transaction.TransactionService;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.PageResponse;
import com.lifepedia.api.shared.Paging;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecurringEntryService {

    private final RecurringEntryRepository repository;
    private final RecurringAdjustmentRepository adjustmentRepository;
    private final CategoryService categoryService;
    private final TransactionService transactionService;
    private final GroupFinanceService groupFinance;
    private final GroupFinanceSync groupSync;

    public RecurringEntryService(RecurringEntryRepository repository, RecurringAdjustmentRepository adjustmentRepository,
                                 CategoryService categoryService, TransactionService transactionService,
                                 GroupFinanceService groupFinance, GroupFinanceSync groupSync) {
        this.repository = repository;
        this.adjustmentRepository = adjustmentRepository;
        this.categoryService = categoryService;
        this.transactionService = transactionService;
        this.groupFinance = groupFinance;
        this.groupSync = groupSync;
    }

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "description", "lower(r.description)",
            "category", "lower(c.name)",
            "day", "r.dayOfMonth",
            "start", "r.startMonth",
            "amount", "r.amount");

    @Transactional(readOnly = true)
    public PageResponse<RecurringEntryResponse> page(UUID userId, EntryType type, int page, int size, String sort) {
        Pageable pageable = Paging.pageable(page, size, sort, SORT_FIELDS, "amount,desc", "r.id");
        return PageResponse.of(repository.page(userId, type, pageable), entries -> {
            Map<UUID, List<RecurringAdjustment>> adjustments = adjustmentRepository
                    .findByRecurringEntryIdIn(entries.stream().map(RecurringEntry::getId).toList()).stream()
                    .collect(Collectors.groupingBy(RecurringAdjustment::getRecurringEntryId));
            return entries.stream()
                    .map(e -> RecurringEntryResponse.from(e, adjustments.getOrDefault(e.getId(), List.of())))
                    .toList();
        });
    }

    @Transactional(readOnly = true)
    public List<RecurringEntryResponse> list(UUID userId, EntryType type) {
        List<RecurringEntry> entries = type == null
                ? repository.findByUserIdOrderByTypeAscAmountDesc(userId)
                : repository.findByUserIdAndTypeOrderByAmountDesc(userId, type);
        Map<UUID, List<RecurringAdjustment>> adjustments = adjustmentRepository.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(RecurringAdjustment::getRecurringEntryId));
        return entries.stream()
                .map(e -> RecurringEntryResponse.from(e, adjustments.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    @Transactional
    public RecurringEntryResponse create(UUID userId, RecurringEntryRequest request) {
        categoryService.requireUsable(userId, request.categoryId(), request.type());
        RecurringEntry entry = new RecurringEntry(userId);
        apply(entry, request);
        RecurringEntryResponse response = RecurringEntryResponse.from(repository.save(entry), List.of());
        groupFinance.shareNew(userId, request.groupIds(), request.shareMode(), entry);
        if (request.removeTransactionIds() != null && !request.removeTransactionIds().isEmpty()) {
            transactionService.deleteAll(userId, request.removeTransactionIds());
        }
        return response;
    }

    @Transactional
    public RecurringEntryResponse update(UUID userId, UUID id, RecurringEntryRequest request) {
        RecurringEntry entry = find(userId, id);
        categoryService.requireUsable(userId, request.categoryId(), request.type());
        apply(entry, request);
        groupSync.recurringChanged(entry);
        return RecurringEntryResponse.from(entry, adjustmentRepository.findByRecurringEntryId(id));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repository.delete(find(userId, id));
    }

    /** Define o valor real do fixo no mês (cria ou substitui). */
    @Transactional
    public AdjustmentResponse adjust(UUID userId, UUID id, YearMonth month, BigDecimal amount) {
        RecurringEntry entry = find(userId, id);
        if (!entry.appliesTo(month)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "\"" + entry.getDescription() + "\" não está vigente em " + month + ".");
        }
        RecurringAdjustment adjustment = findAdjustment(id, month)
                .orElseGet(() -> new RecurringAdjustment(userId, id, month, amount));
        adjustment.setAmount(amount);
        return AdjustmentResponse.from(adjustmentRepository.save(adjustment));
    }

    @Transactional
    public void removeAdjustment(UUID userId, UUID id, YearMonth month) {
        find(userId, id);
        adjustmentRepository.delete(findAdjustment(id, month)
                .orElseThrow(() -> new NotFoundException("Ajuste não encontrado.")));
    }

    private Optional<RecurringAdjustment> findAdjustment(UUID entryId, YearMonth month) {
        return adjustmentRepository.findByRecurringEntryId(entryId).stream()
                .filter(a -> a.getMonth().equals(month))
                .findFirst();
    }

    private RecurringEntry find(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Lançamento fixo não encontrado."));
    }

    private static void apply(RecurringEntry entry, RecurringEntryRequest request) {
        YearMonth start = request.startMonth() != null ? request.startMonth()
                : entry.getStartMonth() != null ? entry.getStartMonth() : YearMonth.now();
        if (request.endMonth() != null && request.endMonth().isBefore(start)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O mês final não pode ser anterior ao mês inicial.");
        }
        entry.setDescription(request.description().trim());
        entry.setType(request.type());
        entry.setCategoryId(request.categoryId());
        entry.setAmount(request.amount());
        entry.setDayOfMonth(request.dayOfMonth());
        entry.setStartMonth(start);
        entry.setEndMonth(request.endMonth());
        entry.setActive(request.active() == null || request.active());
        entry.setNotes(request.notes());
    }
}
