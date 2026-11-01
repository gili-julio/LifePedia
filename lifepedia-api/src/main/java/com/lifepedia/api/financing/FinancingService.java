package com.lifepedia.api.financing;

import com.lifepedia.api.finance.recurring.ScheduledFixed;
import com.lifepedia.api.financing.FinancingCost.Frequency;
import com.lifepedia.api.financing.FinancingDtos.Cost;
import com.lifepedia.api.financing.FinancingDtos.CostRequest;
import com.lifepedia.api.financing.FinancingDtos.ExtraPayment;
import com.lifepedia.api.financing.FinancingDtos.ExtraPreview;
import com.lifepedia.api.financing.FinancingDtos.ExtraRequest;
import com.lifepedia.api.financing.FinancingDtos.FinancingData;
import com.lifepedia.api.financing.FinancingDtos.FinancingRequest;
import com.lifepedia.api.financing.FinancingDtos.FinancingResponse;
import com.lifepedia.api.financing.FinancingDtos.FinancingSummary;
import com.lifepedia.api.financing.FinancingDtos.Member;
import com.lifepedia.api.financing.FinancingDtos.MyFixedRequest;
import com.lifepedia.api.financing.FinancingDtos.NextPayment;
import com.lifepedia.api.financing.FinancingDtos.Progress;
import com.lifepedia.api.financing.FinancingDtos.ShareRequest;
import com.lifepedia.api.financing.FinancingMath.Extra;
import com.lifepedia.api.financing.FinancingMath.Plan;
import com.lifepedia.api.financing.FinancingMath.Row;
import com.lifepedia.api.financing.FinancingMath.Totals;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRemovedEvent;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.group.UserGroup;
import com.lifepedia.api.group.UserGroupRepository;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Financiamentos em andamento (pessoais ou de grupo): parcelas, saldo, partes de cada um, extras, gastos e avisos. */
@Service
public class FinancingService {

    static final int MAX_FINANCINGS = 50;
    static final int MAX_EXTRAS = 120;
    static final int MAX_COSTS = 30;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String FIXED_NOTE = "Parcelas do financiamento, atualizadas sozinhas. Para mudar, edite o financiamento.";

    private final FinancingRepository financings;
    private final FinancingMemberRepository members;
    private final FinancingExtraPaymentRepository extras;
    private final FinancingCostRepository costs;
    private final GroupMemberRepository groupMembers;
    private final UserGroupRepository groups;
    private final UserRepository users;
    private final FinancingRateService rates;
    private final ScheduledFixed fixed;
    private final NotificationService notifications;
    private final Clock clock;

    public FinancingService(FinancingRepository financings, FinancingMemberRepository members,
                            FinancingExtraPaymentRepository extras, FinancingCostRepository costs,
                            GroupMemberRepository groupMembers, UserGroupRepository groups, UserRepository users,
                            FinancingRateService rates, ScheduledFixed fixed, NotificationService notifications,
                            Clock clock) {
        this.financings = financings;
        this.members = members;
        this.extras = extras;
        this.costs = costs;
        this.groupMembers = groupMembers;
        this.groups = groups;
        this.users = users;
        this.rates = rates;
        this.fixed = fixed;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FinancingSummary> list(FinancingScope scope) {
        FinancingOwner owner = scope.owner();
        return summaries(owner.isGroup() ? financings.findByGroupIdOrderByCreatedAtAsc(owner.groupId())
                : financings.findByUserIdOrderByCreatedAtAsc(owner.userId()), scope.userId());
    }

    /** Todos os financiamentos de que a pessoa participa (pessoais e de grupos). */
    @Transactional(readOnly = true)
    public List<FinancingSummary> overview(UUID userId) {
        Set<UUID> ids = members.findByUserId(userId).stream().map(FinancingMember::getFinancingId)
                .collect(Collectors.toSet());
        return summaries(financings.findAllById(ids).stream()
                .sorted(Comparator.comparing(Financing::getCreatedAt)).toList(), userId);
    }

    @Transactional(readOnly = true)
    public FinancingResponse get(FinancingScope scope, UUID id) {
        return response(scope, find(scope, id));
    }

    @Transactional
    public FinancingResponse create(FinancingScope scope, FinancingRequest request) {
        FinancingOwner owner = scope.owner();
        long count = owner.isGroup() ? financings.countByGroupId(owner.groupId()) : financings.countByUserId(owner.userId());
        if (count >= MAX_FINANCINGS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_FINANCINGS + " financiamentos.");
        }
        Financing financing = new Financing(owner, scope.userId());
        apply(financing, request);
        financings.save(financing);
        if (owner.isGroup()) {
            replaceShares(financing, request.shares() != null ? request.shares() : equalShares(owner.groupId()));
        } else {
            members.save(new FinancingMember(financing.getId(), scope.userId(), HUNDRED));
        }
        return response(scope, financing);
    }

    @Transactional
    public FinancingResponse update(FinancingScope scope, UUID id, FinancingRequest request) {
        Financing financing = find(scope, id);
        apply(financing, request);
        if (scope.owner().isGroup() && request.shares() != null) replaceShares(financing, request.shares());
        syncFixed(financing);
        return response(scope, financing);
    }

    /** Só quem cadastrou ou admin; os fixos gerados pelas parcelas saem junto. */
    @Transactional
    public void delete(FinancingScope scope, UUID id) {
        Financing financing = find(scope, id);
        if (!scope.canDelete(financing.getCreatedBy())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Só quem cadastrou ou um administrador pode excluir.");
        }
        for (FinancingMember m : members.findByFinancingIdOrderByCreatedAtAsc(id)) {
            if (m.getRecurringEntryId() != null) fixed.delete(m.getUserId(), m.getRecurringEntryId());
        }
        financings.delete(financing);
    }

    /** Compara o financiamento sem e com uma amortização extra (nada é salvo). */
    @Transactional(readOnly = true)
    public ExtraPreview previewExtra(FinancingScope scope, UUID id, ExtraRequest request) {
        Financing financing = find(scope, id);
        List<FinancingExtraPayment> saved = extras.findByFinancingIdOrderByMonthAsc(id);
        List<Row> before = schedule(financing, saved);
        requireExtraMonth(financing, before, request.month());
        List<Extra> all = new ArrayList<>(toExtras(saved));
        all.add(new Extra(request.month(), request.amount().doubleValue(), request.mode()));
        List<Row> after = FinancingMath.schedule(plan(financing, List.of()).withExtras(all));
        Totals a = FinancingMath.totals(before);
        Totals b = FinancingMath.totals(after);
        return new ExtraPreview(a.months(), b.months(), a.lastMonth(), b.lastMonth(),
                paymentAfter(before, request.month()), paymentAfter(after, request.month()),
                a.totalInterest().subtract(b.totalInterest()).max(BigDecimal.ZERO),
                a.totalPaid().subtract(b.totalPaid()).max(BigDecimal.ZERO));
    }

    @Transactional
    public FinancingResponse addExtra(FinancingScope scope, UUID id, ExtraRequest request) {
        Financing financing = find(scope, id);
        if (extras.countByFinancingId(id) >= MAX_EXTRAS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_EXTRAS + " amortizações extras.");
        }
        requireExtraMonth(financing, schedule(financing, extras.findByFinancingIdOrderByMonthAsc(id)), request.month());
        extras.save(new FinancingExtraPayment(id, request.month(), request.amount(), request.mode()));
        syncFixed(financing);
        return response(scope, financing);
    }

    @Transactional
    public FinancingResponse removeExtra(FinancingScope scope, UUID id, UUID extraId) {
        Financing financing = find(scope, id);
        extras.delete(extras.findByIdAndFinancingId(extraId, id)
                .orElseThrow(() -> new NotFoundException("Amortização não encontrada.")));
        extras.flush();
        syncFixed(financing);
        return response(scope, financing);
    }

    @Transactional
    public FinancingResponse addCost(FinancingScope scope, UUID id, CostRequest request) {
        Financing financing = find(scope, id);
        if (costs.countByFinancingId(id) >= MAX_COSTS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_COSTS + " gastos.");
        }
        FinancingCost cost = new FinancingCost(id);
        applyCost(cost, request);
        costs.save(cost);
        return response(scope, financing);
    }

    @Transactional
    public FinancingResponse updateCost(FinancingScope scope, UUID id, UUID costId, CostRequest request) {
        Financing financing = find(scope, id);
        applyCost(findCost(id, costId), request);
        return response(scope, financing);
    }

    @Transactional
    public FinancingResponse removeCost(FinancingScope scope, UUID id, UUID costId) {
        Financing financing = find(scope, id);
        costs.delete(findCost(id, costId));
        return response(scope, financing);
    }

    /** Liga ou desliga o fixo pessoal com a parte da pessoa nas parcelas. */
    @Transactional
    public FinancingResponse myFixed(FinancingScope scope, UUID id, MyFixedRequest request) {
        Financing financing = find(scope, id);
        FinancingMember me = members.findByFinancingIdAndUserId(id, scope.userId())
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "Você não tem parte neste financiamento."));
        if (Boolean.TRUE.equals(request.enabled())) {
            if (me.getRecurringEntryId() == null) {
                if (request.categoryId() == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha a categoria.");
                SortedMap<YearMonth, BigDecimal> amounts = amounts(financing, me, scheduleOf(financing));
                if (amounts.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Sua parte está em 0%.");
                UUID groupId = financing.getGroupId() != null && Boolean.TRUE.equals(request.shareToGroup())
                        ? financing.getGroupId() : null;
                me.setRecurringEntryId(fixed.create(scope.userId(), financing.getName(), request.categoryId(),
                        financing.getDueDay(), FIXED_NOTE, amounts, groupId));
            }
        } else if (me.getRecurringEntryId() != null) {
            fixed.delete(scope.userId(), me.getRecurringEntryId());
            me.setRecurringEntryId(null);
        }
        return response(scope, financing);
    }

    /** Avisa os participantes das parcelas e gastos que vencem daqui a {@code reminderDays} dias. */
    @Transactional
    public int sendReminders(LocalDate today) {
        int sent = 0;
        for (Financing f : financings.findByReminderDaysIsNotNull()) {
            LocalDate target = today.plusDays(f.getReminderDays());
            List<FinancingMember> payers = members.findByFinancingIdOrderByCreatedAtAsc(f.getId()).stream()
                    .filter(m -> m.getShare().signum() > 0).toList();
            if (payers.isEmpty()) continue;
            String groupName = f.getGroupId() == null ? null : groups.findById(f.getGroupId()).map(UserGroup::getName).orElse(null);
            Optional<Row> due = scheduleOf(f).stream().filter(r -> f.dueDate(r.month()).equals(target)).findFirst();
            for (FinancingMember m : payers) {
                if (due.isPresent()) {
                    notifications.notify(m.getUserId(), NotificationType.FINANCING_DUE, null, f.getGroupId(),
                            data(f, groupName, part(due.get().payment(), m.getShare()), target, null));
                    sent++;
                }
                for (FinancingCost c : costs.findByFinancingIdOrderByCreatedAtAsc(f.getId())) {
                    if (!c.nextDate(target).equals(target)) continue;
                    notifications.notify(m.getUserId(), NotificationType.FINANCING_COST_DUE, null, f.getGroupId(),
                            data(f, groupName, part(c.getAmount(), m.getShare()), target, c.getName()));
                    sent++;
                }
            }
        }
        return sent;
    }

    /** Financiamentos pessoais para o arquivo da conta. */
    @Transactional(readOnly = true)
    public List<FinancingData> export(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        return financings.findByUserIdOrderByCreatedAtAsc(userId).stream().map(f -> new FinancingData(f.getName(),
                f.getKind(), f.getCondition(), f.getPrice(), f.getDownPayment(), f.getFgts(), f.getFinanced(),
                f.getMonthlyRate(), f.getSystem(), f.getMonths(), f.getFirstMonth(), f.getDueDay(), f.getInsuranceRate(),
                f.getMonthlyFee(), f.getIndex(), f.getBank(), f.getNotes(),
                extras.findByFinancingIdOrderByMonthAsc(f.getId()).stream().map(FinancingService::extra).toList(),
                costs.findByFinancingIdOrderByCreatedAtAsc(f.getId()).stream().map(c -> cost(c, today)).toList())).toList();
    }

    /** Quem sai do grupo deixa de ter parte nos financiamentos dele. */
    @EventListener
    public void onMemberRemoved(GroupMemberRemovedEvent event) {
        members.deleteByGroupMember(event.groupId(), event.userId());
    }

    private Financing find(FinancingScope scope, UUID id) {
        FinancingOwner owner = scope.owner();
        return financings.findById(id)
                .filter(f -> owner.isGroup() ? owner.groupId().equals(f.getGroupId())
                        : f.getGroupId() == null && owner.userId().equals(f.getUserId()))
                .orElseThrow(() -> new NotFoundException("Financiamento não encontrado."));
    }

    private FinancingCost findCost(UUID financingId, UUID costId) {
        return costs.findByIdAndFinancingId(costId, financingId)
                .orElseThrow(() -> new NotFoundException("Gasto não encontrado."));
    }

    private Plan plan(Financing f, List<FinancingExtraPayment> saved) {
        return new Plan(f.getFinanced().doubleValue(), f.getMonthlyRate().doubleValue() / 100, f.getMonths(), f.getSystem(),
                f.getInsuranceRate().doubleValue() / 100, f.getMonthlyFee().doubleValue(), f.getFirstMonth(),
                rates.correction(f.getIndex()), toExtras(saved));
    }

    private List<Row> schedule(Financing f, List<FinancingExtraPayment> saved) {
        return FinancingMath.schedule(plan(f, saved));
    }

    private List<Row> scheduleOf(Financing f) {
        return schedule(f, extras.findByFinancingIdOrderByMonthAsc(f.getId()));
    }

    private Progress progress(Financing f, List<Row> rows, LocalDate today) {
        int paid = (int) rows.stream().filter(r -> f.dueDate(r.month()).isBefore(today)).count();
        BigDecimal balance = paid == 0 ? f.getFinanced() : rows.get(paid - 1).balance();
        BigDecimal amortized = f.getFinanced().subtract(balance).max(BigDecimal.ZERO);
        BigDecimal percent = amortized.multiply(HUNDRED).divide(f.getFinanced(), 1, RoundingMode.HALF_UP).min(HUNDRED);
        BigDecimal interestPaid = BigDecimal.ZERO;
        BigDecimal interestLeft = BigDecimal.ZERO;
        BigDecimal paymentsLeft = BigDecimal.ZERO;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (i < paid) {
                interestPaid = interestPaid.add(r.interest());
            } else {
                interestLeft = interestLeft.add(r.interest());
                paymentsLeft = paymentsLeft.add(r.payment()).add(r.extra());
            }
        }
        Row next = paid < rows.size() ? rows.get(paid) : null;
        BigDecimal payoff = next == null ? BigDecimal.ZERO : balance;
        return new Progress(paid, rows.size() - paid, rows.size(), rows.isEmpty() ? null : rows.getLast().month(),
                payoff, percent, interestPaid, interestLeft,
                next == null ? null : new NextPayment(next.number(), next.month(), f.dueDate(next.month()), next.payment()),
                payoff, paymentsLeft.subtract(payoff).max(BigDecimal.ZERO));
    }

    private FinancingResponse response(FinancingScope scope, Financing f) {
        LocalDate today = LocalDate.now(clock);
        List<FinancingExtraPayment> saved = extras.findByFinancingIdOrderByMonthAsc(f.getId());
        List<Row> rows = schedule(f, saved);
        Progress progress = progress(f, rows, today);
        List<FinancingMember> payers = members.findByFinancingIdOrderByCreatedAtAsc(f.getId());
        Set<UUID> userIds = new HashSet<>(payers.stream().map(FinancingMember::getUserId).toList());
        if (f.getCreatedBy() != null) userIds.add(f.getCreatedBy());
        Map<UUID, User> people = users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        BigDecimal next = progress.next() == null ? BigDecimal.ZERO : progress.next().amount();
        List<Member> memberList = payers.stream().filter(m -> people.containsKey(m.getUserId())).map(m -> {
            boolean me = m.getUserId().equals(scope.userId());
            return new Member(UserSummary.from(people.get(m.getUserId())), m.getShare(), part(next, m.getShare()),
                    me ? m.getRecurringEntryId() : null, m.getRecurringEntryId() != null, me);
        }).toList();
        User creator = f.getCreatedBy() == null ? null : people.get(f.getCreatedBy());
        return new FinancingResponse(f.getId(), f.getGroupId(), f.getName(), f.getKind(), f.getCondition(), f.getPrice(),
                f.getDownPayment(), f.getFgts(), f.getFinanced(), f.getMonthlyRate(),
                FinancingMath.percent(FinancingMath.yearly(f.getMonthlyRate().doubleValue() / 100)), f.getSystem(),
                f.getMonths(), f.getFirstMonth(), f.getDueDay(), f.getInsuranceRate(), f.getMonthlyFee(), f.getIndex(),
                f.getIndex() == CorrectionIndex.NONE ? null : FinancingMath.percent(rates.projection(f.getIndex())),
                f.getBank(), f.getReminderDays(), f.getNotes(), progress, memberList,
                saved.stream().map(FinancingService::extra).toList(),
                costs.findByFinancingIdOrderByCreatedAtAsc(f.getId()).stream().map(c -> cost(c, today)).toList(),
                rows, creator == null ? null : UserSummary.from(creator), scope.canDelete(f.getCreatedBy()));
    }

    private List<FinancingSummary> summaries(List<Financing> items, UUID userId) {
        if (items.isEmpty()) return List.of();
        LocalDate today = LocalDate.now(clock);
        List<UUID> ids = items.stream().map(Financing::getId).toList();
        Map<UUID, List<FinancingExtraPayment>> extrasById = extras.findByFinancingIdIn(ids).stream()
                .collect(Collectors.groupingBy(FinancingExtraPayment::getFinancingId));
        Map<UUID, BigDecimal> myShare = members.findByFinancingIdIn(ids).stream()
                .filter(m -> m.getUserId().equals(userId))
                .collect(Collectors.toMap(FinancingMember::getFinancingId, FinancingMember::getShare));
        Map<UUID, String> groupNames = groups.findAllById(items.stream().map(Financing::getGroupId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(UserGroup::getId, UserGroup::getName));
        return items.stream().map(f -> {
            List<FinancingExtraPayment> saved = extrasById.getOrDefault(f.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(FinancingExtraPayment::getMonth)).toList();
            return new FinancingSummary(f.getId(), f.getGroupId(), groupNames.get(f.getGroupId()), f.getName(),
                    f.getKind(), f.getFinanced(), progress(f, schedule(f, saved), today), myShare.get(f.getId()));
        }).toList();
    }

    private void apply(Financing f, FinancingRequest r) {
        f.setName(r.name().trim());
        f.setKind(r.kind());
        f.setCondition(r.condition());
        f.setPrice(r.price());
        f.setDownPayment(r.downPayment());
        f.setFgts(r.kind().isHousing() ? r.fgts() : null);
        f.setFinanced(r.financed());
        f.setMonthlyRate(r.monthlyRate());
        f.setSystem(r.system());
        f.setMonths(r.months());
        f.setFirstMonth(r.firstMonth());
        f.setDueDay(r.dueDay());
        f.setInsuranceRate(r.insuranceRate() == null ? BigDecimal.ZERO : r.insuranceRate());
        f.setMonthlyFee(r.monthlyFee() == null ? BigDecimal.ZERO : r.monthlyFee());
        f.setIndex(r.index() == null ? CorrectionIndex.NONE : r.index());
        f.setBank(r.bank() == null || r.bank().isBlank() ? null : r.bank().trim());
        f.setReminderDays(r.reminderDays());
        f.setNotes(r.notes() == null || r.notes().isBlank() ? null : r.notes().trim());
    }

    private static void applyCost(FinancingCost cost, CostRequest r) {
        if (r.frequency() == Frequency.YEARLY && r.dueMonth() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Informe o mês do gasto anual.");
        }
        cost.set(r.name().trim(), r.amount(), r.frequency(), r.dueMonth(), r.dueDay());
    }

    private List<ShareRequest> equalShares(UUID groupId) {
        List<GroupMember> all = groupMembers.findByGroupIdOrderByCreatedAtAsc(groupId);
        BigDecimal each = HUNDRED.divide(BigDecimal.valueOf(all.size()), 2, RoundingMode.DOWN);
        BigDecimal rest = HUNDRED.subtract(each.multiply(BigDecimal.valueOf(all.size())));
        List<ShareRequest> shares = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            shares.add(new ShareRequest(all.get(i).getUserId(), i == 0 ? each.add(rest) : each));
        }
        return shares;
    }

    /** Partes do grupo: só membros, sem repetir, somando 100%; quem sai da lista perde o fixo gerado. */
    private void replaceShares(Financing f, Collection<ShareRequest> shares) {
        Set<UUID> groupUsers = groupMembers.findByGroupIdOrderByCreatedAtAsc(f.getGroupId()).stream()
                .map(GroupMember::getUserId).collect(Collectors.toSet());
        Map<UUID, BigDecimal> wanted = new HashMap<>();
        for (ShareRequest s : shares) {
            if (!groupUsers.contains(s.userId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Só membros do grupo podem ter parte.");
            }
            if (wanted.put(s.userId(), s.share()) != null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Cada pessoa aparece uma vez só.");
            }
        }
        BigDecimal total = wanted.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(HUNDRED) != 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "As partes precisam somar 100%.");
        Map<UUID, FinancingMember> current = members.findByFinancingIdOrderByCreatedAtAsc(f.getId()).stream()
                .collect(Collectors.toMap(FinancingMember::getUserId, Function.identity()));
        for (FinancingMember m : current.values()) {
            if (wanted.containsKey(m.getUserId())) continue;
            if (m.getRecurringEntryId() != null) fixed.delete(m.getUserId(), m.getRecurringEntryId());
            members.delete(m);
        }
        wanted.forEach((userId, share) -> {
            FinancingMember m = current.get(userId);
            if (m == null) members.save(new FinancingMember(f.getId(), userId, share));
            else m.setShare(share);
        });
        members.flush();
    }

    /** Refaz os fixos ligados (valor de cada mês); fixo excluído pela pessoa ou parte zerada desliga. */
    private void syncFixed(Financing f) {
        List<Row> rows = null;
        for (FinancingMember m : members.findByFinancingIdOrderByCreatedAtAsc(f.getId())) {
            if (m.getRecurringEntryId() == null) continue;
            if (rows == null) rows = scheduleOf(f);
            SortedMap<YearMonth, BigDecimal> amounts = amounts(f, m, rows);
            if (amounts.isEmpty()) {
                fixed.delete(m.getUserId(), m.getRecurringEntryId());
                m.setRecurringEntryId(null);
            } else if (!fixed.update(m.getUserId(), m.getRecurringEntryId(), f.getName(), f.getDueDay(), amounts)) {
                m.setRecurringEntryId(null);
            }
        }
    }

    private static SortedMap<YearMonth, BigDecimal> amounts(Financing f, FinancingMember m, List<Row> rows) {
        SortedMap<YearMonth, BigDecimal> amounts = new TreeMap<>();
        for (Row r : rows) {
            BigDecimal value = part(r.payment(), m.getShare());
            if (value.signum() > 0) amounts.put(r.month(), value);
        }
        return amounts;
    }

    private static void requireExtraMonth(Financing f, List<Row> rows, YearMonth month) {
        if (month.isBefore(f.getFirstMonth()) || rows.isEmpty() || month.isAfter(rows.getLast().month())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O mês precisa estar entre a 1ª e a última parcela.");
        }
    }

    private static BigDecimal paymentAfter(List<Row> rows, YearMonth month) {
        return rows.stream().filter(r -> r.month().isAfter(month)).findFirst().map(Row::payment).orElse(BigDecimal.ZERO);
    }

    private static List<Extra> toExtras(List<FinancingExtraPayment> saved) {
        return saved.stream().map(e -> new Extra(e.getMonth(), e.getAmount().doubleValue(), e.getMode())).toList();
    }

    private static BigDecimal part(BigDecimal amount, BigDecimal share) {
        return amount.multiply(share).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static Map<String, String> data(Financing f, String groupName, BigDecimal amount, LocalDate date, String costName) {
        Map<String, String> data = new HashMap<>();
        data.put("financingId", f.getId().toString());
        data.put("financingName", f.getName());
        data.put("amount", amount.toPlainString());
        data.put("date", date.toString());
        if (groupName != null) data.put("groupName", groupName);
        if (costName != null) data.put("costName", costName);
        return data;
    }

    private static ExtraPayment extra(FinancingExtraPayment e) {
        return new ExtraPayment(e.getId(), e.getMonth(), e.getAmount(), e.getMode());
    }

    private static Cost cost(FinancingCost c, LocalDate today) {
        return new Cost(c.getId(), c.getName(), c.getAmount(), c.getFrequency(), c.getDueMonth(), c.getDueDay(),
                c.nextDate(today));
    }
}
