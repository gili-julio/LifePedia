package com.lifepedia.api.finance.group;

import com.lifepedia.api.finance.recurring.RecurringEntry;
import com.lifepedia.api.finance.transaction.Transaction;
import com.lifepedia.api.group.GroupMemberRemovedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Mantém os itens de grupo coerentes com os pessoais (edição, categoria, saída do grupo). Exclusão: FK em cascata. */
@Component
public class GroupFinanceSync {

    private final GroupTransactionRepository transactions;
    private final GroupRecurringEntryRepository recurring;

    public GroupFinanceSync(GroupTransactionRepository transactions, GroupRecurringEntryRepository recurring) {
        this.transactions = transactions;
        this.recurring = recurring;
    }

    @Transactional
    public void transactionChanged(Transaction t) {
        transactions.sync(t.getId(), t.getDescription(), t.getType(), t.getCategoryId(), t.getAmount(), t.getDate(),
                t.getMonth(), t.getNotes());
    }

    @Transactional
    public void recurringChanged(RecurringEntry e) {
        recurring.sync(e.getId(), e.getDescription(), e.getType(), e.getCategoryId(), e.getAmount(), e.getDayOfMonth(),
                e.getStartMonth(), e.getEndMonth(), e.isActive(), e.getNotes());
    }

    /** Cópias em grupos que usam a categoria (os sincronizados já contam no pessoal). */
    @Transactional(readOnly = true)
    public long copiesUsing(UUID categoryId) {
        return transactions.countByCategoryIdAndSourceIdIsNull(categoryId) + recurring.countByCategoryIdAndSourceIdIsNull(categoryId);
    }

    @Transactional
    public void reassignCategory(UUID userId, UUID from, UUID to) {
        transactions.reassignCategory(userId, from, to);
        recurring.reassignCategory(userId, from, to);
    }

    /** Quem sai do grupo leva seus itens junto. */
    @EventListener
    public void onMemberRemoved(GroupMemberRemovedEvent event) {
        transactions.deleteByMember(event.groupId(), event.userId());
        recurring.deleteByMember(event.groupId(), event.userId());
    }
}
