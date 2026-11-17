package com.lifepedia.api.finance.wallet;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.finance.Installments;
import com.lifepedia.api.finance.Installments.Installment;
import com.lifepedia.api.finance.bank.BankRef;
import com.lifepedia.api.finance.category.Category;
import com.lifepedia.api.finance.category.CategoryService;
import com.lifepedia.api.finance.group.GroupFinanceSync;
import com.lifepedia.api.finance.group.ShareMode;
import com.lifepedia.api.finance.recurring.RecurringAdjustment;
import com.lifepedia.api.finance.recurring.RecurringAdjustmentRepository;
import com.lifepedia.api.finance.recurring.RecurringEntry;
import com.lifepedia.api.finance.recurring.RecurringEntryRepository;
import com.lifepedia.api.finance.transaction.Transaction;
import com.lifepedia.api.finance.transaction.TransactionDtos.TransactionRequest;
import com.lifepedia.api.finance.transaction.TransactionRepository;
import com.lifepedia.api.finance.transaction.TransactionService;
import com.lifepedia.api.finance.wallet.WalletDtos.AccountRequest;
import com.lifepedia.api.finance.wallet.WalletDtos.AccountView;
import com.lifepedia.api.finance.wallet.WalletDtos.BankAccountData;
import com.lifepedia.api.finance.wallet.WalletDtos.BalanceRequest;
import com.lifepedia.api.finance.wallet.WalletDtos.CardData;
import com.lifepedia.api.finance.wallet.WalletDtos.CardDetail;
import com.lifepedia.api.finance.wallet.WalletDtos.CardRequest;
import com.lifepedia.api.finance.wallet.WalletDtos.CardView;
import com.lifepedia.api.finance.wallet.WalletDtos.GroupRef;
import com.lifepedia.api.finance.wallet.WalletDtos.PayRequest;
import com.lifepedia.api.finance.wallet.WalletDtos.PaymentData;
import com.lifepedia.api.finance.wallet.WalletDtos.PurchaseRequest;
import com.lifepedia.api.finance.wallet.WalletDtos.PurchaseResult;
import com.lifepedia.api.finance.wallet.WalletDtos.RecalculateItem;
import com.lifepedia.api.finance.wallet.WalletDtos.RecalculateRequest;
import com.lifepedia.api.finance.wallet.WalletDtos.RecalculateResult;
import com.lifepedia.api.finance.wallet.WalletDtos.Wallet;
import com.lifepedia.api.finance.wallet.WalletDtos.WalletData;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRemovedEvent;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.group.UserGroup;
import com.lifepedia.api.group.UserGroupRepository;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Carteira: cartões com as faturas, contas com o saldo projetado, lançamento rápido e recálculo das faturas. */
@Service
public class WalletService {

    static final int MAX_CARDS = 20;
    static final int MAX_ACCOUNTS = 20;
    private static final int MONTHS_AROUND = 6;
    private static final String OTHERS = "outros";

    private final CardRepository cards;
    private final BankAccountRepository accounts;
    private final InvoiceRepository invoices;
    private final TransactionRepository transactions;
    private final TransactionService transactionService;
    private final RecurringEntryRepository recurring;
    private final RecurringAdjustmentRepository adjustments;
    private final CategoryService categories;
    private final GroupFinanceSync groupSync;
    private final GroupMemberRepository members;
    private final UserGroupRepository groups;
    private final Clock clock;

    public WalletService(CardRepository cards, BankAccountRepository accounts, InvoiceRepository invoices,
                         TransactionRepository transactions, TransactionService transactionService,
                         RecurringEntryRepository recurring, RecurringAdjustmentRepository adjustments,
                         CategoryService categories, GroupFinanceSync groupSync, GroupMemberRepository members,
                         UserGroupRepository groups, Clock clock) {
        this.cards = cards;
        this.accounts = accounts;
        this.invoices = invoices;
        this.transactions = transactions;
        this.transactionService = transactionService;
        this.recurring = recurring;
        this.adjustments = adjustments;
        this.categories = categories;
        this.groupSync = groupSync;
        this.members = members;
        this.groups = groups;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Wallet wallet(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        Owner owner = owner(userId);
        Map<UUID, CardBook> books = books(owner, today, null);
        List<CardView> cardViews = owner.cards.stream().map(c -> view(books.get(c.getId()), groupNames(c))).toList();
        List<AccountView> accountViews = owner.accounts.stream().map(a -> accountView(a, owner, books.values(), today)).toList();
        BigDecimal open = cardViews.stream().map(c -> c.current().total().max(BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal toPay = cardViews.stream().filter(c -> c.pending() != null).map(c -> c.pending().total())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Wallet(cardViews, accountViews, open, toPay, today);
    }

    /** Fatura de um mês (padrão: a aberta) com os itens, a previsão e as faturas vizinhas. */
    @Transactional(readOnly = true)
    public CardDetail card(UUID userId, UUID cardId, YearMonth month) {
        Card card = findCard(userId, cardId);
        LocalDate today = LocalDate.now(clock);
        Owner owner = owner(userId);
        CardBook book = books(owner, today, month).get(card.getId());
        YearMonth shown = month != null ? month : book.current();
        return new CardDetail(view(book, groupNames(card)), book.detail(shown, categories.mapByIdFor(userId)),
                book.history(shown, MONTHS_AROUND), book.upcoming(shown, MONTHS_AROUND));
    }

    @Transactional
    public CardDetail createCard(UUID userId, CardRequest request) {
        if (cards.countByUserId(userId) >= MAX_CARDS) throw bad("Você chegou ao limite de " + MAX_CARDS + " cartões.");
        Card card = new Card(userId);
        apply(card, userId, request);
        cards.saveAndFlush(card);
        return card(userId, card.getId(), null);
    }

    @Transactional
    public CardDetail updateCard(UUID userId, UUID cardId, CardRequest request) {
        Card card = findCard(userId, cardId);
        apply(card, userId, request);
        cards.saveAndFlush(card);
        return card(userId, card.getId(), null);
    }

    /** Os lançamentos continuam; só o cartão (ciclo, pagamentos e avisos) sai. */
    @Transactional
    public void deleteCard(UUID userId, UUID cardId) {
        cards.delete(findCard(userId, cardId));
    }

    @Transactional
    public AccountView createAccount(UUID userId, AccountRequest request) {
        if (accounts.countByUserId(userId) >= MAX_ACCOUNTS) throw bad("Você chegou ao limite de " + MAX_ACCOUNTS + " contas.");
        BankAccount account = new BankAccount(userId);
        apply(account, request);
        accounts.saveAndFlush(account);
        return accountView(userId, account);
    }

    @Transactional
    public AccountView updateAccount(UUID userId, UUID accountId, AccountRequest request) {
        BankAccount account = findAccount(userId, accountId);
        apply(account, request);
        accounts.saveAndFlush(account);
        return accountView(userId, account);
    }

    @Transactional
    public AccountView setBalance(UUID userId, UUID accountId, BalanceRequest request) {
        BankAccount account = findAccount(userId, accountId);
        account.setBalance(request.balance());
        account.setBalanceOn(balanceDay(request.balanceOn()));
        return accountView(userId, account);
    }

    @Transactional
    public void deleteAccount(UUID userId, UUID accountId) {
        accounts.delete(findAccount(userId, accountId));
    }

    /** Marca a fatura como paga; não vira lançamento (os gastos já estão nas compras). */
    @Transactional
    public CardDetail pay(UUID userId, UUID cardId, YearMonth month, PayRequest request) {
        Card card = findCard(userId, cardId);
        LocalDate today = LocalDate.now(clock);
        CardBook book = books(owner(userId), today, month).get(card.getId());
        UUID accountId = request.accountId() != null ? findAccount(userId, request.accountId()).getId() : payingAccount(card);
        BigDecimal amount = request.amount() != null ? request.amount() : book.total(month).max(BigDecimal.ZERO);
        LocalDate paidOn = request.paidOn() != null ? request.paidOn() : today;
        if (paidOn.isAfter(today)) throw bad("A data do pagamento não pode ser no futuro.");
        Invoice invoice = invoices.findByCardIdAndMonth(card.getId(), month).orElseGet(() -> new Invoice(card.getId(), month));
        invoice.pay(paidOn, amount, accountId);
        invoices.save(invoice);
        return card(userId, cardId, month);
    }

    @Transactional
    public CardDetail unpay(UUID userId, UUID cardId, YearMonth month) {
        Card card = findCard(userId, cardId);
        invoices.findByCardIdAndMonth(card.getId(), month).ifPresent(Invoice::unpay);
        return card(userId, cardId, month);
    }

    /** Lançamentos feitos à mão desde {@code from} que estão numa fatura diferente da que o ciclo indica. */
    @Transactional(readOnly = true)
    public List<RecalculateItem> recalculatePreview(UUID userId, UUID cardId, YearMonth from) {
        Card card = findCard(userId, cardId);
        return outOfCycle(card, from).stream()
                .map(t -> new RecalculateItem(t.getId(), t.getDescription(), t.getDate(), t.getAmount(), t.getMonth(),
                        expected(card, t)))
                .toList();
    }

    /** Move para a fatura certa só os escolhidos que ainda estão fora do ciclo. */
    @Transactional
    public RecalculateResult recalculate(UUID userId, UUID cardId, RecalculateRequest request) {
        Card card = findCard(userId, cardId);
        Set<UUID> ids = new HashSet<>(request.ids());
        List<Transaction> moving = outOfCycle(card, request.from()).stream().filter(t -> ids.contains(t.getId())).toList();
        moving.forEach(t -> t.setMonth(expected(card, t)));
        // A sincronização limpa o contexto do JPA: todos os meses mudam antes dela.
        moving.forEach(groupSync::transactionChanged);
        return new RecalculateResult(moving.size());
    }

    /** Lançamento rápido: no cartão vai para a fatura da compra (parcelas nas seguintes); na conta, no mês da data. */
    @Transactional
    public PurchaseResult purchase(UUID userId, PurchaseRequest request) {
        if ((request.cardId() == null) == (request.accountId() == null)) throw bad("Escolha um cartão ou uma conta.");
        LocalDate date = request.date() != null ? request.date() : LocalDate.now(clock);
        int count = request.installments() == null ? 1 : request.installments();
        BankRef bank;
        YearMonth first;
        if (request.cardId() != null) {
            Card card = findCard(userId, request.cardId());
            bank = card.bank();
            first = card.cycle().invoiceOf(date);
        } else {
            if (count > 1) throw bad("Parcelas só no cartão.");
            bank = findAccount(userId, request.accountId()).bank();
            first = YearMonth.from(date);
        }
        String description = request.description().trim();
        UUID categoryId = request.categoryId() != null
                ? categories.requireUsable(userId, request.categoryId(), EntryType.EXPENSE).getId()
                : suggestCategory(userId, description);
        BigDecimal part = request.amount().divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal firstPart = request.amount().subtract(part.multiply(BigDecimal.valueOf(count - 1L)));
        for (int i = 1; i <= count; i++) {
            String name = count > 1 ? "%s (%d/%d)".formatted(description, i, count) : description;
            transactionService.create(userId, new TransactionRequest(name, EntryType.EXPENSE, categoryId, i == 1 ? firstPart : part,
                    date, first.plusMonths(i - 1L), null, null, bank, request.groupIds(), ShareMode.SYNC));
        }
        return new PurchaseResult(count, first, categoryId);
    }

    @Transactional(readOnly = true)
    public WalletData exportData(UUID userId) {
        List<Card> own = cards.findByUserIdOrderByCreatedAtAsc(userId);
        Map<UUID, List<Invoice>> paid = invoices.findByCardIdIn(own.stream().map(Card::getId).toList()).stream()
                .filter(Invoice::isPaid).sorted(Comparator.comparing(Invoice::getMonth))
                .collect(Collectors.groupingBy(Invoice::getCardId));
        return new WalletData(
                own.stream().map(c -> new CardData(c.bank().label(), c.getNickname(), c.getLastDigits(), c.getColor(),
                        c.getClosingDay(), c.getDueDay(), c.getCreditLimit(), c.getBudget(), c.isReminders(),
                        paid.getOrDefault(c.getId(), List.of()).stream()
                                .map(i -> new PaymentData(i.getMonth(), i.getPaidOn(), i.getPaidAmount())).toList())).toList(),
                accounts.findByUserIdOrderByCreatedAtAsc(userId).stream()
                        .map(a -> new BankAccountData(a.bank().label(), a.getNickname(), a.getBalance(), a.getBalanceOn())).toList());
    }

    /** Quem sai do grupo deixa de mostrar os cartões nele. */
    @EventListener
    public void onMemberRemoved(GroupMemberRemovedEvent event) {
        cards.sharedBy(event.userId(), event.groupId()).forEach(c -> c.getGroupIds().remove(event.groupId()));
    }

    /** Livros de todos os cartões da pessoa; {@code around} garante os lançamentos desse mês. */
    Map<UUID, CardBook> books(Owner owner, LocalDate today, YearMonth around) {
        Map<UUID, List<Invoice>> byCard = invoices.findByCardIdIn(owner.cards.stream().map(Card::getId).toList()).stream()
                .collect(Collectors.groupingBy(Invoice::getCardId));
        return owner.cards.stream().collect(Collectors.toMap(Card::getId, card -> {
            YearMonth current = card.cycle().invoiceOf(today);
            YearMonth from = (around != null && around.isBefore(current) ? around : current).minusMonths(13);
            YearMonth to = (around != null && around.isAfter(current) ? around : current).plusMonths(60);
            List<Transaction> own = transactions.ofBankInMonths(owner.userId, card.getBankCode(), from, to).stream()
                    .filter(t -> card.matches(t.getBank())).toList();
            List<RecurringEntry> fixed = owner.fixed.stream().filter(e -> card.matches(e.getBank())).toList();
            Map<YearMonth, Invoice> states = byCard.getOrDefault(card.getId(), List.of()).stream()
                    .collect(Collectors.toMap(Invoice::getMonth, Function.identity()));
            LocalDate since = card.getCreatedAt().atZoneSameInstant(clock.getZone()).toLocalDate();
            return new CardBook(card, today, since, own, fixed, owner.adjustments, states);
        }));
    }

    /** Tudo da pessoa que as contas da carteira usam. */
    Owner owner(UUID userId) {
        Map<UUID, Map<YearMonth, BigDecimal>> adjusted = adjustments.findByUserId(userId).stream().collect(Collectors.groupingBy(
                RecurringAdjustment::getRecurringEntryId,
                Collectors.toMap(RecurringAdjustment::getMonth, RecurringAdjustment::getAmount)));
        return new Owner(userId, cards.findByUserIdOrderByCreatedAtAsc(userId), accounts.findByUserIdOrderByCreatedAtAsc(userId),
                recurring.findByUserIdOrderByTypeAscAmountDesc(userId), adjusted);
    }

    static CardView view(CardBook book, List<GroupRef> groupRefs) {
        Card card = book.card();
        BankRef bank = card.bank();
        return new CardView(card.getId(), bank.key(), bank, card.label(), card.getNickname(), card.getLastDigits(), card.getColor(),
                card.getClosingDay(), card.getDueDay(), card.getCreditLimit(), card.getBudget(), card.isReminders(),
                card.getAccountId(), groupRefs, book.summary(book.current()), book.pending().orElse(null), book.limit());
    }

    /** Dados da pessoa carregados de uma vez. */
    record Owner(UUID userId, List<Card> cards, List<BankAccount> accounts, List<RecurringEntry> fixed,
                 Map<UUID, Map<YearMonth, BigDecimal>> adjustments) {
    }

    private AccountView accountView(UUID userId, BankAccount account) {
        LocalDate today = LocalDate.now(clock);
        Owner owner = owner(userId);
        return accountView(account, owner, books(owner, today, null).values(), today);
    }

    /** Saldo informado + entradas e saídas depois dele; no fim do mês, também as faturas que esta conta paga. */
    private AccountView accountView(BankAccount account, Owner owner, Collection<CardBook> books, LocalDate today) {
        LocalDate since = account.getBalanceOn();
        LocalDate endOfMonth = YearMonth.from(today).atEndOfMonth();
        BigDecimal untilToday = account.getBalance();
        BigDecimal rest = BigDecimal.ZERO;
        for (Transaction t : transactions.ofBankSince(owner.userId, account.getBankCode(), since.plusDays(1))) {
            if (!account.matches(t.getBank()) || t.getDate().isAfter(endOfMonth)) continue;
            BigDecimal value = t.getType() == EntryType.INCOME ? t.getAmount() : t.getAmount().negate();
            if (t.getDate().isAfter(today)) rest = rest.add(value);
            else untilToday = untilToday.add(value);
        }
        Function<RecurringEntry, Map<YearMonth, BigDecimal>> adjustedOf = e -> owner.adjustments.getOrDefault(e.getId(), Map.of());
        for (RecurringEntry e : owner.fixed) {
            if (!account.matches(e.getBank())) continue;
            for (YearMonth m = YearMonth.from(since); !m.isAfter(YearMonth.from(endOfMonth)); m = m.plusMonths(1)) {
                if (!e.appliesTo(m)) continue;
                LocalDate day = m.atDay(Math.min(e.getDayOfMonth() == null ? 1 : e.getDayOfMonth(), m.lengthOfMonth()));
                if (!day.isAfter(since)) continue;
                BigDecimal amount = adjustedOf.apply(e).getOrDefault(m, e.getAmount());
                BigDecimal value = e.getType() == EntryType.INCOME ? amount : amount.negate();
                if (day.isAfter(today)) rest = rest.add(value);
                else untilToday = untilToday.add(value);
            }
        }
        BigDecimal pending = BigDecimal.ZERO;
        for (CardBook book : books) {
            Card card = book.card();
            boolean byDefault = card.getAccountId() == null && account.sameBank(card.bank());
            if (!account.getId().equals(card.getAccountId()) && !byDefault) continue;
            untilToday = untilToday.subtract(book.paid(account.getId(), byDefault, since, today));
            pending = pending.add(book.unpaidDue(since, endOfMonth));
        }
        BankRef bank = account.bank();
        return new AccountView(account.getId(), bank.key(), bank, account.label(), account.getNickname(), account.getBalance(),
                since, untilToday, untilToday.add(rest).subtract(pending), pending);
    }

    private void apply(Card card, UUID userId, CardRequest request) {
        BankRef bank = BankRef.clean(request.bank());
        if (bank == null) throw bad("Informe o banco.");
        boolean taken = cards.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .anyMatch(c -> !c.getId().equals(card.getId()) && c.bank().key().equals(bank.key()));
        if (taken) throw new BusinessException(HttpStatus.CONFLICT, "Esse banco já tem um cartão cadastrado.");
        card.setBank(bank);
        card.setNickname(blankToNull(request.nickname()));
        card.setLastDigits(blankToNull(request.lastDigits()));
        card.setColor(blankToNull(request.color()));
        card.setClosingDay(request.closingDay());
        card.setDueDay(request.dueDay());
        card.setCreditLimit(request.creditLimit());
        card.setBudget(request.budget());
        card.setReminders(!Boolean.FALSE.equals(request.reminders()));
        card.setAccountId(request.accountId() == null ? null : findAccount(userId, request.accountId()).getId());
        Set<UUID> chosen = request.groupIds() == null ? Set.of() : new HashSet<>(request.groupIds());
        Set<UUID> mine = members.findByUserId(userId).stream().map(GroupMember::getGroupId).collect(Collectors.toSet());
        if (!mine.containsAll(chosen)) throw bad("Grupo inválido.");
        card.getGroupIds().retainAll(chosen);
        card.getGroupIds().addAll(chosen);
    }

    private void apply(BankAccount account, AccountRequest request) {
        BankRef bank = BankRef.clean(request.bank());
        if (bank == null) throw bad("Informe o banco.");
        boolean taken = accounts.findByUserIdOrderByCreatedAtAsc(account.getUserId()).stream()
                .anyMatch(a -> !a.getId().equals(account.getId()) && a.bank().key().equals(bank.key()));
        if (taken) throw new BusinessException(HttpStatus.CONFLICT, "Esse banco já tem uma conta cadastrada.");
        account.setBank(bank);
        account.setNickname(blankToNull(request.nickname()));
        account.setBalance(request.balance());
        account.setBalanceOn(balanceDay(request.balanceOn()));
    }

    private LocalDate balanceDay(LocalDate informed) {
        LocalDate today = LocalDate.now(clock);
        if (informed != null && informed.isAfter(today)) throw bad("O dia do saldo não pode ser no futuro.");
        return informed != null ? informed : today;
    }

    /** Conta que paga a fatura: a escolhida no cartão ou a do mesmo banco. */
    private UUID payingAccount(Card card) {
        if (card.getAccountId() != null) return card.getAccountId();
        return accounts.findByUserIdOrderByCreatedAtAsc(card.getUserId()).stream()
                .filter(a -> a.sameBank(card.bank())).map(BankAccount::getId).findFirst().orElse(null);
    }

    /** Feitos à mão (sem descrição do documento), não conferidos e com data desde {@code from}. */
    private List<Transaction> outOfCycle(Card card, YearMonth from) {
        return transactions.ofBankSince(card.getUserId(), card.getBankCode(), from.atDay(1)).stream()
                .filter(t -> card.matches(t.getBank()) && !t.isChecked() && t.getOriginalDescription() == null)
                .filter(t -> !expected(card, t).equals(t.getMonth()))
                .sorted(Comparator.comparing(Transaction::getDate).thenComparing(Transaction::getDescription))
                .toList();
    }

    private static YearMonth expected(Card card, Transaction t) {
        int installment = Installments.parse(t.getDescription()).map(Installment::number).orElse(1);
        return card.cycle().invoiceOf(t.getDate(), installment);
    }

    /** Categoria do último lançamento com o mesmo nome; senão "Outros". */
    private UUID suggestCategory(UUID userId, String description) {
        Optional<UUID> last = transactions.lastWithDescription(userId, EntryType.EXPENSE, TransactionService.normalize(description),
                PageRequest.of(0, 1)).stream().map(Transaction::getCategoryId).findFirst();
        if (last.isPresent()) return last.get();
        List<Category> expenses = categories.mapByIdFor(userId).values().stream().filter(c -> c.getType() == EntryType.EXPENSE)
                .sorted(Comparator.comparing(Category::getName)).toList();
        return expenses.stream().filter(c -> c.getName().trim().toLowerCase(Locale.ROOT).equals(OTHERS)).findFirst()
                .or(() -> expenses.stream().findFirst())
                .map(Category::getId)
                .orElseThrow(() -> bad("Crie uma categoria de despesa antes."));
    }

    private List<GroupRef> groupNames(Card card) {
        if (card.getGroupIds().isEmpty()) return List.of();
        return groups.findAllById(card.getGroupIds()).stream()
                .sorted(Comparator.comparing(UserGroup::getName))
                .map(g -> new GroupRef(g.getId(), g.getName())).toList();
    }

    private Card findCard(UUID userId, UUID cardId) {
        return cards.findByIdAndUserId(cardId, userId).orElseThrow(() -> new NotFoundException("Cartão não encontrado."));
    }

    private BankAccount findAccount(UUID userId, UUID accountId) {
        return accounts.findByIdAndUserId(accountId, userId).orElseThrow(() -> new NotFoundException("Conta não encontrada."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BusinessException bad(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}
