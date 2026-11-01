package com.lifepedia.api.finance.bank;

import com.lifepedia.api.finance.bank.BankDtos.AssignRequest;
import com.lifepedia.api.finance.bank.BankDtos.AssignResult;
import com.lifepedia.api.finance.bank.BankDtos.LastBank;
import com.lifepedia.api.finance.group.GroupFinanceSync;
import com.lifepedia.api.finance.recurring.RecurringEntry;
import com.lifepedia.api.finance.recurring.RecurringEntryRepository;
import com.lifepedia.api.finance.transaction.Transaction;
import com.lifepedia.api.finance.transaction.TransactionRepository;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BankService {

    private final TransactionRepository transactions;
    private final RecurringEntryRepository recurring;
    private final GroupFinanceSync groupSync;

    public BankService(TransactionRepository transactions, RecurringEntryRepository recurring, GroupFinanceSync groupSync) {
        this.transactions = transactions;
        this.recurring = recurring;
        this.groupSync = groupSync;
    }

    /** Banco do item com banco criado por último, entre ocasionais e fixos. */
    @Transactional(readOnly = true)
    public LastBank last(UUID userId) {
        Transaction transaction = transactions.withBank(userId, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        RecurringEntry entry = recurring.withBank(userId, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        if (transaction == null) return new LastBank(entry == null ? null : entry.getBank());
        boolean entryIsNewer = entry != null && entry.getCreatedAt().isAfter(transaction.getCreatedAt());
        return new LastBank(entryIsNewer ? entry.getBank() : transaction.getBank());
    }

    /** Tudo ou nada: todos os itens precisam ser do usuário. Os sincronizados nos grupos acompanham. */
    @Transactional
    public AssignResult assign(UUID userId, AssignRequest request) {
        Set<UUID> transactionIds = new HashSet<>(request.transactionIdsOrEmpty());
        Set<UUID> recurringIds = new HashSet<>(request.recurringIdsOrEmpty());
        if (transactionIds.isEmpty() && recurringIds.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha ao menos um item.");
        }
        BankRef bank = BankRef.clean(request.bank());
        List<Transaction> ownTransactions = transactions.findByUserIdAndIdIn(userId, transactionIds);
        List<RecurringEntry> ownRecurring = recurring.findByUserIdAndIdIn(userId, recurringIds);
        if (ownTransactions.size() != transactionIds.size() || ownRecurring.size() != recurringIds.size()) {
            throw new NotFoundException("Item não encontrado.");
        }
        ownTransactions.forEach(t -> t.setBank(bank));
        ownRecurring.forEach(e -> e.setBank(bank));
        groupSync.bankChanged(transactionIds, recurringIds, bank);
        return new AssignResult(transactionIds.size() + recurringIds.size());
    }
}
