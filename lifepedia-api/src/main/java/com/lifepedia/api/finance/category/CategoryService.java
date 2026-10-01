package com.lifepedia.api.finance.category;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.group.GroupFinanceSync;
import com.lifepedia.api.finance.category.CategoryDtos.CategoryRequest;
import com.lifepedia.api.finance.category.CategoryDtos.CategoryResponse;
import com.lifepedia.api.finance.recurring.RecurringEntryRepository;
import com.lifepedia.api.finance.transaction.TransactionRepository;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.PageResponse;
import com.lifepedia.api.shared.Paging;
import org.springframework.data.domain.Pageable;
import com.lifepedia.api.user.UserRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private static final Collator PT_BR = Collator.getInstance(Locale.of("pt", "BR"));

    private final CategoryRepository repository;
    private final RecurringEntryRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final GroupFinanceSync groupSync;

    public CategoryService(CategoryRepository repository,
                           RecurringEntryRepository recurringRepository,
                           TransactionRepository transactionRepository,
                           GroupFinanceSync groupSync) {
        this.repository = repository;
        this.recurringRepository = recurringRepository;
        this.transactionRepository = transactionRepository;
        this.groupSync = groupSync;
    }

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        repository.saveAll(DefaultCategories.ALL.stream()
                .map(d -> new Category(event.userId(), d.name(), d.type(), d.color()))
                .toList());
    }

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "lower(c.name)",
            "type", "c.type",
            "usage", CategoryRepository.USAGE);

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> page(UUID userId, EntryType type, int page, int size, String sort) {
        Pageable pageable = Paging.pageable(page, size, sort, SORT_FIELDS, "name,asc", "c.id");
        Map<UUID, Long> usage = usageByCategory(userId);
        return PageResponse.of(repository.page(userId, type, pageable), categories -> categories.stream()
                .map(c -> CategoryResponse.from(c, usage.getOrDefault(c.getId(), 0L)))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId, EntryType type) {
        List<Category> categories = type == null
                ? repository.findByUserId(userId)
                : repository.findByUserIdAndType(userId, type);
        Map<UUID, Long> usage = usageByCategory(userId);
        return categories.stream()
                .sorted(Comparator.comparing(Category::getName, PT_BR))
                .map(c -> CategoryResponse.from(c, usage.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoryResponse create(UUID userId, CategoryRequest request) {
        String name = request.name().trim();
        requireUniqueName(userId, request.type(), name, null);
        Category category = repository.save(new Category(userId, name, request.type(), normalizeColor(request.color())));
        return CategoryResponse.from(category, 0);
    }

    @Transactional
    public CategoryResponse update(UUID userId, UUID id, CategoryRequest request) {
        Category category = find(userId, id);
        long usage = usageOf(id);
        if (category.getType() != request.type() && usage > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Não é possível mudar o tipo de uma categoria que já tem lançamentos.");
        }
        String name = request.name().trim();
        requireUniqueName(userId, request.type(), name, id);
        category.setName(name);
        category.setType(request.type());
        category.setColor(normalizeColor(request.color()));
        return CategoryResponse.from(category, usage);
    }

    /** Exclui; se em uso, move os lançamentos para {@code replaceWith} (mesmo tipo). */
    @Transactional
    public void delete(UUID userId, UUID id, UUID replaceWith) {
        Category category = find(userId, id);
        if (repository.countByUserIdAndType(userId, category.getType()) <= 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Mantenha pelo menos uma categoria de " + (category.getType() == EntryType.INCOME ? "ganho." : "despesa."));
        }
        long usage = usageOf(id);
        if (usage > 0) {
            if (replaceWith == null) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Categoria em uso por " + usage + " lançamento(s). Escolha para qual categoria movê-los.");
            }
            Category target = find(userId, replaceWith);
            if (target.getId().equals(id) || target.getType() != category.getType()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha outra categoria do mesmo tipo.");
            }
            recurringRepository.reassignCategory(userId, id, target.getId());
            transactionRepository.reassignCategory(userId, id, target.getId());
            groupSync.reassignCategory(userId, id, target.getId());
        }
        repository.delete(category);
    }

    /** Valida dono e tipo da categoria. */
    @Transactional(readOnly = true)
    public Category requireUsable(UUID userId, UUID categoryId, EntryType type) {
        Category category = repository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "Categoria inválida."));
        requireMatchingType(category, type);
        return category;
    }

    public static void requireMatchingType(Category category, EntryType type) {
        if (category.getType() != type) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "A categoria \"" + category.getName()
                    + "\" é de " + (category.getType() == EntryType.INCOME ? "ganhos" : "despesas") + ".");
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, Category> mapByIdFor(UUID userId) {
        return repository.findByUserId(userId).stream().collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private Category find(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada."));
    }

    private void requireUniqueName(UUID userId, EntryType type, String name, UUID ignoreId) {
        repository.findByUserIdAndTypeAndNameIgnoreCase(userId, type, name)
                .filter(existing -> !existing.getId().equals(ignoreId))
                .ifPresent(existing -> {
                    throw new BusinessException(HttpStatus.CONFLICT, "Já existe uma categoria \"" + existing.getName() + "\".");
                });
    }

    /** Lançamentos pessoais + cópias em grupos. */
    private long usageOf(UUID categoryId) {
        return recurringRepository.countByCategoryId(categoryId) + transactionRepository.countByCategoryId(categoryId)
                + groupSync.copiesUsing(categoryId);
    }

    private Map<UUID, Long> usageByCategory(UUID userId) {
        Map<UUID, Long> usage = new HashMap<>();
        recurringRepository.countByCategory(userId).forEach(u -> usage.merge(u.getCategoryId(), u.getTotal(), Long::sum));
        transactionRepository.countByCategory(userId).forEach(u -> usage.merge(u.getCategoryId(), u.getTotal(), Long::sum));
        return usage;
    }

    private static String normalizeColor(String color) {
        return color.toLowerCase(Locale.ROOT);
    }
}
