package com.lifepedia.api.finance.transaction;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.group.ShareMode;
import com.lifepedia.api.finance.transaction.TransactionDtos.TransactionRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fachada para outros módulos lançarem uma despesa ocasional da pessoa (ex.: gasto do veículo). Passa pelas mesmas
 * regras do financeiro (categoria da pessoa, grupos sincronizados). Lançamento apagado no financeiro só some da ligação.
 */
@Component
public class ExpenseLinks {

    private final TransactionService transactions;
    private final TransactionRepository repository;

    public ExpenseLinks(TransactionService transactions, TransactionRepository repository) {
        this.transactions = transactions;
        this.repository = repository;
    }

    /** @param groupIds grupos da pessoa para onde vai sincronizado */
    @Transactional
    public UUID create(UUID userId, String description, UUID categoryId, BigDecimal amount, LocalDate date, List<UUID> groupIds) {
        return transactions.create(userId, new TransactionRequest(clip(description), EntryType.EXPENSE, categoryId, amount, date,
                null, null, null, null, groupIds, ShareMode.SYNC)).id();
    }

    /** Muda nome, categoria, valor e data; mês, banco e observações ficam como a pessoa deixou no financeiro. */
    @Transactional
    public boolean update(UUID userId, UUID transactionId, String description, UUID categoryId, BigDecimal amount, LocalDate date) {
        Optional<Transaction> found = repository.findByIdAndUserId(transactionId, userId);
        if (found.isEmpty()) return false;
        Transaction t = found.get();
        YearMonth month = t.getMonth().isBefore(YearMonth.from(date)) ? YearMonth.from(date) : t.getMonth();
        transactions.update(userId, transactionId, new TransactionRequest(clip(description), EntryType.EXPENSE, categoryId, amount,
                date, month, t.getNotes(), t.getOriginalDescription(), t.getBank(), null, null));
        return true;
    }

    @Transactional
    public void delete(UUID userId, UUID transactionId) {
        repository.findByIdAndUserId(transactionId, userId).ifPresent(repository::delete);
    }

    /** Categoria de cada lançamento da pessoa que ainda existe. */
    @Transactional(readOnly = true)
    public Map<UUID, UUID> categoriesOf(UUID userId, Collection<UUID> transactionIds) {
        if (transactionIds.isEmpty()) return Map.of();
        return repository.findByUserIdAndIdIn(userId, transactionIds).stream()
                .collect(Collectors.toMap(Transaction::getId, Transaction::getCategoryId));
    }

    private static String clip(String description) {
        String text = description.trim();
        return text.length() > 120 ? text.substring(0, 120).trim() : text;
    }
}
