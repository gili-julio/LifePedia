package com.lifepedia.api.finance.recurring;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.category.CategoryService;
import com.lifepedia.api.finance.group.GroupFinanceService;
import com.lifepedia.api.finance.group.GroupFinanceSync;
import com.lifepedia.api.finance.group.ShareMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.SortedMap;
import java.util.UUID;

/**
 * Despesa fixa mantida por outro módulo (ex.: parcelas de um financiamento): valor de cada mês vira ajuste, o valor
 * cadastrado é o do 1º mês e a vigência vai do 1º ao último mês.
 */
@Service
public class ScheduledFixed {

    private final RecurringEntryRepository entries;
    private final RecurringAdjustmentRepository adjustments;
    private final CategoryService categories;
    private final GroupFinanceService groupFinance;
    private final GroupFinanceSync groupSync;

    public ScheduledFixed(RecurringEntryRepository entries, RecurringAdjustmentRepository adjustments,
                          CategoryService categories, GroupFinanceService groupFinance, GroupFinanceSync groupSync) {
        this.entries = entries;
        this.adjustments = adjustments;
        this.categories = categories;
        this.groupFinance = groupFinance;
        this.groupSync = groupSync;
    }

    /** @param groupId grupo para onde levar sincronizado (nulo = só pessoal) */
    @Transactional
    public UUID create(UUID userId, String description, UUID categoryId, int dayOfMonth, String notes,
                       SortedMap<YearMonth, BigDecimal> amounts, UUID groupId) {
        categories.requireUsable(userId, categoryId, EntryType.EXPENSE);
        RecurringEntry entry = new RecurringEntry(userId);
        entry.setType(EntryType.EXPENSE);
        entry.setCategoryId(categoryId);
        entry.setNotes(notes);
        entry.setActive(true);
        fill(entry, description, dayOfMonth, amounts);
        entries.save(entry);
        writeAdjustments(entry, amounts);
        if (groupId != null) groupFinance.shareNew(userId, List.of(groupId), ShareMode.SYNC, entry);
        return entry.getId();
    }

    /** Atualiza nome, dia e valores; falso se o fixo não existe mais (a pessoa excluiu). */
    @Transactional
    public boolean update(UUID userId, UUID id, String description, int dayOfMonth, SortedMap<YearMonth, BigDecimal> amounts) {
        RecurringEntry entry = entries.findByIdAndUserId(id, userId).orElse(null);
        if (entry == null) return false;
        fill(entry, description, dayOfMonth, amounts);
        adjustments.deleteByRecurringEntryId(id);
        writeAdjustments(entry, amounts);
        groupSync.recurringChanged(entry);
        return true;
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        entries.findByIdAndUserId(id, userId).ifPresent(entries::delete);
    }

    private static void fill(RecurringEntry entry, String description, int dayOfMonth, SortedMap<YearMonth, BigDecimal> amounts) {
        entry.setDescription(description.length() > 120 ? description.substring(0, 120).trim() : description);
        entry.setDayOfMonth(dayOfMonth);
        entry.setAmount(amounts.get(amounts.firstKey()));
        entry.setStartMonth(amounts.firstKey());
        entry.setEndMonth(amounts.lastKey());
    }

    private void writeAdjustments(RecurringEntry entry, SortedMap<YearMonth, BigDecimal> amounts) {
        adjustments.saveAll(amounts.entrySet().stream()
                .filter(e -> e.getValue().compareTo(entry.getAmount()) != 0)
                .map(e -> new RecurringAdjustment(entry.getUserId(), entry.getId(), e.getKey(), e.getValue()))
                .toList());
    }
}
