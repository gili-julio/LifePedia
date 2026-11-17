package com.lifepedia.api.vehicle;

import com.lifepedia.api.finance.transaction.ExpenseLinks;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.shared.image.ImageFile;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import com.lifepedia.api.vehicle.ExpenseKind.Group;
import com.lifepedia.api.vehicle.VehicleAccess.Access;
import com.lifepedia.api.vehicle.VehicleDtos.ConsumptionPoint;
import com.lifepedia.api.vehicle.VehicleDtos.ExpenseRequest;
import com.lifepedia.api.vehicle.VehicleDtos.ExpenseResponse;
import com.lifepedia.api.vehicle.VehicleDtos.FinanceInfo;
import com.lifepedia.api.vehicle.VehicleDtos.FinanceLink;
import com.lifepedia.api.vehicle.VehicleDtos.LogResponse;
import com.lifepedia.api.vehicle.VehicleDtos.MonthStat;
import com.lifepedia.api.vehicle.VehicleDtos.ReadingRequest;
import com.lifepedia.api.vehicle.VehicleDtos.ReadingResponse;
import com.lifepedia.api.vehicle.VehicleDtos.Stats;
import com.lifepedia.api.vehicle.VehicleMath.Consumption;
import com.lifepedia.api.vehicle.VehicleMath.Fill;
import com.lifepedia.api.vehicle.VehicleMath.Reading;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Km, gastos (com o lançamento no financeiro de quem lançou), histórico do que foi feito e números do veículo. */
@Service
public class VehicleLogService {

    static final int EXPENSES_PER_DAY = 200;
    static final int MAX_MONTHS = 36;
    private static final Map<ExpenseKind, String> LABEL = new EnumMap<>(Map.of(
            ExpenseKind.FUEL, "Abastecimento", ExpenseKind.SERVICE, "Manutenção", ExpenseKind.PART, "Peças",
            ExpenseKind.TAX, "IPVA", ExpenseKind.LICENSING, "Licenciamento", ExpenseKind.INSURANCE, "Seguro",
            ExpenseKind.FINE, "Multa", ExpenseKind.TOLL, "Pedágio e estacionamento", ExpenseKind.WASH, "Lavagem",
            ExpenseKind.OTHER, "Gasto"));

    private final VehicleExpenseRepository expenses;
    private final VehicleReadingRepository readings;
    private final VehicleServiceLogRepository logs;
    private final VehicleAccess access;
    private final UserRepository users;
    private final VehicleImageStore images;
    private final ExpenseLinks finance;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public VehicleLogService(VehicleExpenseRepository expenses, VehicleReadingRepository readings, VehicleServiceLogRepository logs,
                             VehicleAccess access, UserRepository users, VehicleImageStore images, ExpenseLinks finance,
                             RateLimiter rateLimiter, Clock clock) {
        this.expenses = expenses;
        this.readings = readings;
        this.logs = logs;
        this.access = access;
        this.users = users;
        this.images = images;
        this.finance = finance;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** Leituras do odômetro, da mais nova para a mais antiga. */
    @Transactional(readOnly = true)
    public List<ReadingResponse> readings(UUID userId, UUID vehicleId) {
        Access found = access.require(vehicleId, userId, VehicleRole.VIEW);
        List<VehicleReading> list = new ArrayList<>(readings.findByVehicleIdOrderByReadOnAscKmAsc(vehicleId));
        Collections.reverse(list);
        Map<UUID, User> people = people(list.stream().map(VehicleReading::getUserId).toList());
        return list.stream().map(r -> new ReadingResponse(r.getId(), r.getKm(), r.getReadOn(), summary(people, r.getUserId()),
                r.getExpenseId() != null, r.getExpenseId() == null && canChange(found, userId, r.getUserId()))).toList();
    }

    @Transactional
    public ReadingResponse addReading(UUID userId, UUID vehicleId, ReadingRequest request) {
        Vehicle vehicle = access.require(vehicleId, userId, VehicleRole.LOG).vehicle();
        requireRate(userId);
        LocalDate date = notFuture(request.date());
        checkKm(vehicleId, request.km(), date, null);
        VehicleReading reading = readings.save(new VehicleReading(vehicleId, userId, null, request.km(), date));
        refreshOdometer(vehicle);
        return new ReadingResponse(reading.getId(), reading.getKm(), reading.getReadOn(), summary(people(List.of(userId)), userId),
                false, true);
    }

    /** Leitura de um gasto muda pelo gasto; a dos outros só quem edita apaga. */
    @Transactional
    public void deleteReading(UUID userId, UUID vehicleId, UUID readingId) {
        Access found = access.require(vehicleId, userId, VehicleRole.LOG);
        VehicleReading reading = readings.findByIdAndVehicleId(readingId, vehicleId)
                .orElseThrow(() -> new NotFoundException("Leitura não encontrada."));
        if (reading.getExpenseId() != null) throw bad("Essa leitura veio de um gasto: mude o km no gasto.");
        if (!canChange(found, userId, reading.getUserId())) throw forbidden("Só quem lançou ou quem edita o veículo apaga.");
        readings.delete(reading);
        readings.flush();
        refreshOdometer(found.vehicle());
    }

    /** Gastos dos últimos {@code months} meses (contando o atual), do mais novo para o mais antigo. */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> expenses(UUID userId, UUID vehicleId, int months) {
        Access found = access.require(vehicleId, userId, VehicleRole.VIEW);
        LocalDate from = YearMonth.now(clock).minusMonths(clampMonths(months) - 1L).atDay(1);
        return responses(found, userId, expenses.findByVehicleIdAndSpentOnGreaterThanEqualOrderBySpentOnDescCreatedAtDesc(vehicleId, from));
    }

    @Transactional
    public ExpenseResponse createExpense(UUID userId, UUID vehicleId, ExpenseRequest r) {
        Access found = access.require(vehicleId, userId, VehicleRole.LOG);
        VehicleExpense expense = addExpense(found.vehicle(), userId, r.kind(), r.date(), r.amount(), r.description(), r.odometer(),
                r.notes(), r.finance());
        if (r.kind() == ExpenseKind.FUEL) {
            expense.setFuelData(r.liters(), r.fuel(), Boolean.TRUE.equals(r.fullTank()), clean(r.station()));
        }
        return responses(found, userId, List.of(expense)).getFirst();
    }

    /** Só quem lançou edita; o lançamento no financeiro acompanha (ou é criado/apagado). */
    @Transactional
    public ExpenseResponse updateExpense(UUID userId, UUID vehicleId, UUID expenseId, ExpenseRequest r) {
        Access found = access.require(vehicleId, userId, VehicleRole.LOG);
        VehicleExpense expense = expense(vehicleId, expenseId);
        if (!userId.equals(expense.getUserId())) throw forbidden("Só quem lançou edita o gasto.");
        LocalDate date = notFuture(r.date());
        VehicleReading reading = readings.findByExpenseId(expenseId).orElse(null);
        if (r.odometer() != null) checkKm(vehicleId, r.odometer(), date, reading == null ? null : reading.getId());
        String description = clean(r.description());
        expense.set(r.kind(), date, r.amount(), description, r.odometer(), clean(r.notes()));
        if (r.kind() == ExpenseKind.FUEL) expense.setFuelData(r.liters(), r.fuel(), Boolean.TRUE.equals(r.fullTank()), clean(r.station()));
        else expense.setFuelData(null, null, false, null);
        if (r.odometer() == null && reading != null) readings.delete(reading);
        else if (r.odometer() != null && reading != null) reading.set(r.odometer(), date);
        else if (r.odometer() != null) readings.save(new VehicleReading(vehicleId, userId, expenseId, r.odometer(), date));
        syncFinance(userId, found.vehicle(), expense, r.finance());
        readings.flush();
        refreshOdometer(found.vehicle());
        return responses(found, userId, List.of(expense)).getFirst();
    }

    /** Quem lançou apaga (o lançamento no financeiro vai junto); quem edita o veículo também, mas aí o financeiro da pessoa fica. */
    @Transactional
    public void deleteExpense(UUID userId, UUID vehicleId, UUID expenseId) {
        Access found = access.require(vehicleId, userId, VehicleRole.LOG);
        VehicleExpense expense = expense(vehicleId, expenseId);
        if (!canChange(found, userId, expense.getUserId())) throw forbidden("Só quem lançou ou quem edita o veículo apaga.");
        if (userId.equals(expense.getUserId()) && expense.getTransactionId() != null) finance.delete(userId, expense.getTransactionId());
        images.deleteAfterCommit(expense.getImageKey());
        expenses.delete(expense);
        expenses.flush();
        refreshOdometer(found.vehicle());
    }

    @Transactional
    public ExpenseResponse setExpenseImage(UUID userId, UUID vehicleId, UUID expenseId, byte[] bytes) {
        Access found = access.require(vehicleId, userId, VehicleRole.LOG);
        VehicleExpense expense = expense(vehicleId, expenseId);
        if (!userId.equals(expense.getUserId())) throw forbidden("Só quem lançou muda o comprovante.");
        String previous = expense.getImageKey();
        expense.setImageKey(images.saveReceipt(bytes));
        images.deleteAfterCommit(previous);
        return responses(found, userId, List.of(expense)).getFirst();
    }

    @Transactional
    public ExpenseResponse removeExpenseImage(UUID userId, UUID vehicleId, UUID expenseId) {
        Access found = access.require(vehicleId, userId, VehicleRole.LOG);
        VehicleExpense expense = expense(vehicleId, expenseId);
        if (!canChange(found, userId, expense.getUserId())) throw forbidden("Só quem lançou muda o comprovante.");
        images.deleteAfterCommit(expense.getImageKey());
        expense.setImageKey(null);
        return responses(found, userId, List.of(expense)).getFirst();
    }

    @Transactional(readOnly = true)
    public ImageFile expenseImage(UUID userId, UUID vehicleId, UUID expenseId) {
        access.require(vehicleId, userId, VehicleRole.VIEW);
        VehicleExpense expense = expense(vehicleId, expenseId);
        if (expense.getImageKey() == null) throw new NotFoundException("Sem comprovante.");
        return images.file(expense.getImageKey());
    }

    /** O que foi feito (itens e revisões), do mais novo; até 100. */
    @Transactional(readOnly = true)
    public List<LogResponse> logs(UUID userId, UUID vehicleId) {
        access.require(vehicleId, userId, VehicleRole.VIEW);
        List<VehicleServiceLog> list = logs.findByVehicleIdOrderByDoneOnDescCreatedAtDesc(vehicleId, PageRequest.of(0, 100));
        Map<UUID, User> people = people(list.stream().map(VehicleServiceLog::getUserId).toList());
        Map<UUID, BigDecimal> amounts = expenses.findAllById(list.stream().map(VehicleServiceLog::getExpenseId).filter(Objects::nonNull)
                .distinct().toList()).stream().collect(Collectors.toMap(VehicleExpense::getId, VehicleExpense::getAmount));
        return list.stream().map(l -> new LogResponse(l.getId(), l.getName(), l.getDoneOn(), l.getKm(), l.getNotes(),
                summary(people, l.getUserId()), l.getExpenseId(), amounts.get(l.getExpenseId()), l.getItemId(), l.getRevisionId()))
                .toList();
    }

    /** Gasto por mês e tipo, consumo pelo tanque cheio, custo por km e preço médio do litro no período. */
    @Transactional(readOnly = true)
    public Stats stats(UUID userId, UUID vehicleId, int months) {
        access.require(vehicleId, userId, VehicleRole.VIEW);
        int span = clampMonths(months);
        YearMonth last = YearMonth.now(clock);
        YearMonth first = last.minusMonths(span - 1L);
        LocalDate from = first.atDay(1);
        List<VehicleExpense> period = expenses.findByVehicleIdAndSpentOnGreaterThanEqualOrderBySpentOnDescCreatedAtDesc(vehicleId, from);

        Map<YearMonth, Map<Group, BigDecimal>> byMonth = new HashMap<>();
        for (VehicleExpense e : period) {
            byMonth.computeIfAbsent(YearMonth.from(e.getSpentOn()), k -> new EnumMap<>(Group.class))
                    .merge(e.getKind().group(), e.getAmount(), BigDecimal::add);
        }
        List<MonthStat> stats = new ArrayList<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            Map<Group, BigDecimal> sums = byMonth.getOrDefault(m, Map.of());
            BigDecimal fuel = sums.getOrDefault(Group.FUEL, BigDecimal.ZERO);
            BigDecimal maintenance = sums.getOrDefault(Group.MAINTENANCE, BigDecimal.ZERO);
            BigDecimal docs = sums.getOrDefault(Group.DOCUMENTS, BigDecimal.ZERO);
            BigDecimal other = sums.getOrDefault(Group.OTHER, BigDecimal.ZERO);
            stats.add(new MonthStat(m, fuel, maintenance, docs, other, fuel.add(maintenance).add(docs).add(other)));
        }
        BigDecimal total = period.stream().map(VehicleExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Fill> fills = expenses.findByVehicleIdAndKindOrderBySpentOnAscOdometerAsc(vehicleId, ExpenseKind.FUEL).stream()
                .map(e -> new Fill(e.getSpentOn(), e.getOdometer(), e.getLiters(), e.isFullTank())).toList();
        List<Consumption> all = VehicleMath.consumption(fills);
        List<Consumption> inPeriod = all.stream().filter(c -> !c.date().isBefore(from)).toList();
        int kmSum = inPeriod.stream().mapToInt(Consumption::km).sum();
        BigDecimal litersSum = inPeriod.stream().map(Consumption::liters).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = litersSum.signum() == 0 ? null : BigDecimal.valueOf(kmSum).divide(litersSum, 2, RoundingMode.HALF_UP);

        List<VehicleExpense> fuelWithLiters = period.stream()
                .filter(e -> e.getKind() == ExpenseKind.FUEL && e.getLiters() != null && e.getLiters().signum() > 0).toList();
        BigDecimal fuelLiters = fuelWithLiters.stream().map(VehicleExpense::getLiters).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fuelPaid = fuelWithLiters.stream().map(VehicleExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgPrice = fuelLiters.signum() == 0 ? null : fuelPaid.divide(fuelLiters, 3, RoundingMode.HALF_UP);

        Integer km = VehicleMath.kmDriven(readings.findByVehicleIdOrderByReadOnAscKmAsc(vehicleId).stream()
                .map(r -> new Reading(r.getReadOn(), r.getKm())).toList(), from, LocalDate.now(clock));
        BigDecimal perKm = km == null ? null : total.divide(BigDecimal.valueOf(km), 2, RoundingMode.HALF_UP);
        List<ConsumptionPoint> points = inPeriod.stream()
                .map(c -> new ConsumptionPoint(c.date(), c.kmPerLiter(), c.km(), c.liters())).toList();
        return new Stats(span, stats, points,
                total, km, perKm, avg, all.isEmpty() ? null : all.getLast().kmPerLiter(), avgPrice,
                fuelLiters.signum() == 0 ? null : fuelLiters);
    }

    /** Grava o gasto, a leitura do km (se houver) e o lançamento no financeiro de quem lançou (também usado ao marcar feito). */
    VehicleExpense addExpense(Vehicle vehicle, UUID userId, ExpenseKind kind, LocalDate rawDate, BigDecimal amount, String description,
                              Integer odometer, String notes, FinanceLink link) {
        requireRate(userId);
        LocalDate date = notFuture(rawDate);
        if (odometer != null) checkKm(vehicle.getId(), odometer, date, null);
        VehicleExpense expense = new VehicleExpense(vehicle.getId(), userId);
        expense.set(kind, date, amount, clean(description), odometer, clean(notes));
        expenses.save(expense);
        if (odometer != null) {
            readings.save(new VehicleReading(vehicle.getId(), userId, expense.getId(), odometer, date));
            refreshOdometer(vehicle);
        }
        if (link != null) {
            expense.setTransactionId(finance.create(userId, financeText(vehicle, expense), link.categoryId(), amount, date,
                    link.groupIds()));
        }
        return expense;
    }

    /** Maior leitura (por data, depois km) vira o odômetro do veículo. */
    void refreshOdometer(Vehicle vehicle) {
        List<VehicleReading> list = readings.findByVehicleIdOrderByReadOnAscKmAsc(vehicle.getId());
        if (list.isEmpty()) vehicle.setOdometer(0, null);
        else vehicle.setOdometer(list.getLast().getKm(), list.getLast().getReadOn());
    }

    private void syncFinance(UUID userId, Vehicle vehicle, VehicleExpense expense, FinanceLink link) {
        UUID transactionId = expense.getTransactionId();
        if (link == null) {
            if (transactionId != null) finance.delete(userId, transactionId);
            expense.setTransactionId(null);
            return;
        }
        String text = financeText(vehicle, expense);
        if (transactionId != null
                && finance.update(userId, transactionId, text, link.categoryId(), expense.getAmount(), expense.getSpentOn())) {
            return;
        }
        expense.setTransactionId(finance.create(userId, text, link.categoryId(), expense.getAmount(), expense.getSpentOn(),
                link.groupIds()));
    }

    private List<ExpenseResponse> responses(Access found, UUID userId, List<VehicleExpense> list) {
        Map<UUID, User> people = people(list.stream().map(VehicleExpense::getUserId).toList());
        Map<UUID, UUID> categories = finance.categoriesOf(userId, list.stream()
                .filter(e -> userId.equals(e.getUserId()) && e.getTransactionId() != null).map(VehicleExpense::getTransactionId).toList());
        return list.stream().map(e -> {
            boolean mine = userId.equals(e.getUserId());
            BigDecimal price = e.getLiters() == null || e.getLiters().signum() == 0 ? null
                    : e.getAmount().divide(e.getLiters(), 3, RoundingMode.HALF_UP);
            UUID category = mine && e.getTransactionId() != null ? categories.get(e.getTransactionId()) : null;
            return new ExpenseResponse(e.getId(), e.getKind(), e.getSpentOn(), e.getAmount(), e.getDescription(), e.getOdometer(),
                    e.getLiters(), price, e.getFuel(), e.isFullTank(), e.getStation(), e.getNotes(), e.imageUrl(),
                    summary(people, e.getUserId()), mine, canChange(found, userId, e.getUserId()),
                    category == null ? null : new FinanceInfo(e.getTransactionId(), category));
        }).toList();
    }

    /** Km coerente com as outras leituras: não menor que uma anterior nem maior que uma posterior. */
    private void checkKm(UUID vehicleId, int km, LocalDate date, UUID ignore) {
        Integer before = readings.maxBefore(vehicleId, date, ignore);
        if (before != null && km < before) throw bad("O km é menor que o de uma leitura anterior (" + before + " km).");
        Integer after = readings.minAfter(vehicleId, date, ignore);
        if (after != null && km > after) throw bad("O km é maior que o de uma leitura posterior (" + after + " km).");
    }

    private LocalDate notFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now(clock))) throw bad("A data não pode ser no futuro.");
        return date;
    }

    private void requireRate(UUID userId) {
        if (!rateLimiter.tryAcquire("vehicle-log:" + userId, EXPENSES_PER_DAY, Duration.ofDays(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Limite de " + EXPENSES_PER_DAY + " lançamentos por dia.");
        }
    }

    private VehicleExpense expense(UUID vehicleId, UUID expenseId) {
        return expenses.findByIdAndVehicleId(expenseId, vehicleId).orElseThrow(() -> new NotFoundException("Gasto não encontrado."));
    }

    private Map<UUID, User> people(Collection<UUID> ids) {
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return Map.of();
        return users.findAllById(distinct).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static String financeText(Vehicle vehicle, VehicleExpense expense) {
        String what = expense.getDescription() != null ? expense.getDescription() : LABEL.get(expense.getKind());
        return what + " - " + vehicle.getName();
    }

    private static boolean canChange(Access found, UUID userId, UUID authorId) {
        return userId.equals(authorId) || found.can(VehicleRole.EDIT);
    }

    private static UserSummary summary(Map<UUID, User> people, UUID id) {
        User user = id == null ? null : people.get(id);
        return user == null ? null : UserSummary.from(user);
    }

    private static int clampMonths(int months) {
        return Math.max(1, Math.min(MAX_MONTHS, months));
    }

    private static String clean(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static BusinessException bad(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    private static BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }
}
