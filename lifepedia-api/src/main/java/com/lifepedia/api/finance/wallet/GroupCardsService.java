package com.lifepedia.api.finance.wallet;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.group.GroupRecurringEntry;
import com.lifepedia.api.finance.group.GroupRecurringEntryRepository;
import com.lifepedia.api.finance.group.GroupTransaction;
import com.lifepedia.api.finance.group.GroupTransactionRepository;
import com.lifepedia.api.finance.wallet.WalletDtos.GroupCard;
import com.lifepedia.api.finance.wallet.WalletService.Owner;
import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Cartões que os membros mostram no grupo: fatura aberta e quanto do gasto do grupo está em cada um. */
@Service
public class GroupCardsService {

    private final GroupAccess access;
    private final GroupMemberRepository members;
    private final CardRepository cards;
    private final WalletService wallet;
    private final GroupTransactionRepository groupTransactions;
    private final GroupRecurringEntryRepository groupRecurring;
    private final UserRepository users;
    private final Clock clock;

    public GroupCardsService(GroupAccess access, GroupMemberRepository members, CardRepository cards, WalletService wallet,
                             GroupTransactionRepository groupTransactions, GroupRecurringEntryRepository groupRecurring,
                             UserRepository users, Clock clock) {
        this.access = access;
        this.members = members;
        this.cards = cards;
        this.wallet = wallet;
        this.groupTransactions = groupTransactions;
        this.groupRecurring = groupRecurring;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GroupCard> cards(UUID userId, UUID groupId) {
        access.requireMember(groupId, userId);
        Set<UUID> memberIds = members.findByGroupIdOrderByCreatedAtAsc(groupId).stream().map(GroupMember::getUserId)
                .collect(Collectors.toSet());
        List<Card> shared = cards.sharedWith(groupId).stream().filter(c -> memberIds.contains(c.getUserId())).toList();
        if (shared.isEmpty()) return List.of();
        LocalDate today = LocalDate.now(clock);
        Map<UUID, User> people = users.findAllById(shared.stream().map(Card::getUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<GroupRecurringEntry> fixed = groupRecurring.findByGroupId(groupId);
        Map<UUID, Map<UUID, CardBook>> booksByOwner = new HashMap<>();
        Map<UUID, Owner> owners = new HashMap<>();
        return shared.stream().map(card -> {
            Owner owner = owners.computeIfAbsent(card.getUserId(), wallet::owner);
            CardBook book = booksByOwner.computeIfAbsent(card.getUserId(), id -> wallet.books(owner, today, null)).get(card.getId());
            YearMonth current = book.current();
            BigDecimal spent = groupTransactions.findByGroupIdAndMonth(groupId, current).stream()
                    .filter(t -> t.getUserId().equals(card.getUserId()) && card.matches(t.getBank()))
                    .map(GroupCardsService::signed).reduce(BigDecimal.ZERO, BigDecimal::add);
            for (GroupRecurringEntry e : fixed) {
                if (!e.getUserId().equals(card.getUserId()) || !card.matches(e.getBank()) || !e.appliesTo(current)) continue;
                BigDecimal amount = e.isSynced()
                        ? owner.adjustments().getOrDefault(e.getSourceId(), Map.of()).getOrDefault(current, e.getAmount())
                        : e.getAmount();
                spent = e.getType() == EntryType.INCOME ? spent.subtract(amount) : spent.add(amount);
            }
            User person = people.get(card.getUserId());
            return new GroupCard(card.getId(), person == null ? null : UserSummary.from(person), card.getUserId().equals(userId),
                    card.label(), card.bank(), card.getLastDigits(), card.getColor(), book.summary(current), spent);
        }).toList();
    }

    private static BigDecimal signed(GroupTransaction t) {
        return t.getType() == EntryType.INCOME ? t.getAmount().negate() : t.getAmount();
    }
}
