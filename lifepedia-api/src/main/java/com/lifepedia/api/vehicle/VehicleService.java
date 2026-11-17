package com.lifepedia.api.vehicle;

import com.lifepedia.api.group.GroupMemberRemovedEvent;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.shared.image.ImageFile;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import com.lifepedia.api.vehicle.FipeClient.FipeException;
import com.lifepedia.api.vehicle.FipeClient.Price;
import com.lifepedia.api.vehicle.VehicleAccess.Access;
import com.lifepedia.api.vehicle.VehicleAccess.Grant;
import com.lifepedia.api.vehicle.VehicleDtos.DocumentData;
import com.lifepedia.api.vehicle.VehicleDtos.ExpenseData;
import com.lifepedia.api.vehicle.VehicleDtos.FipeInfo;
import com.lifepedia.api.vehicle.VehicleDtos.FipeOption;
import com.lifepedia.api.vehicle.VehicleDtos.FipePrice;
import com.lifepedia.api.vehicle.VehicleDtos.FipeRef;
import com.lifepedia.api.vehicle.VehicleDtos.ItemData;
import com.lifepedia.api.vehicle.VehicleDtos.ReadingData;
import com.lifepedia.api.vehicle.VehicleDtos.RevisionData;
import com.lifepedia.api.vehicle.VehicleDtos.Summary;
import com.lifepedia.api.vehicle.VehicleDtos.SummaryItem;
import com.lifepedia.api.vehicle.VehicleDtos.Upcoming;
import com.lifepedia.api.vehicle.VehicleDtos.VehicleCard;
import com.lifepedia.api.vehicle.VehicleDtos.VehicleData;
import com.lifepedia.api.vehicle.VehicleDtos.VehicleDetail;
import com.lifepedia.api.vehicle.VehicleDtos.VehicleList;
import com.lifepedia.api.vehicle.VehicleDtos.VehicleRequest;
import com.lifepedia.api.vehicle.VehicleMath.Stage;
import com.lifepedia.api.vehicle.VehicleTemplates.Template;
import com.lifepedia.api.vehicle.VehicleViews.Loaded;
import com.lifepedia.api.vehicle.VehicleViews.Pace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Veículos: cadastro, lista (seus e compartilhados), detalhe com os vencimentos, foto e Tabela FIPE. */
@Service
public class VehicleService {

    static final int MAX_VEHICLES = 20;
    static final int FIPE_LOOKUPS_PER_MINUTE = 60;
    private static final Logger log = LoggerFactory.getLogger(VehicleService.class);
    private static final Duration FIPE_REFRESH = Duration.ofMinutes(10);
    private static final int FIPE_BATCH = 200;

    private final VehicleRepository vehicles;
    private final VehicleReadingRepository readings;
    private final VehicleItemRepository items;
    private final VehicleRevisionRepository revisions;
    private final VehicleDocumentRepository documents;
    private final VehicleExpenseRepository expenses;
    private final VehicleShareRepository shares;
    private final VehicleAccess access;
    private final UserRepository users;
    private final VehicleImageStore images;
    private final FipeClient fipe;
    private final RateLimiter rateLimiter;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public VehicleService(VehicleRepository vehicles, VehicleReadingRepository readings, VehicleItemRepository items,
                          VehicleRevisionRepository revisions, VehicleDocumentRepository documents, VehicleExpenseRepository expenses,
                          VehicleShareRepository shares, VehicleAccess access, UserRepository users, VehicleImageStore images,
                          FipeClient fipe, RateLimiter rateLimiter, PlatformTransactionManager transactions, Clock clock) {
        this.vehicles = vehicles;
        this.readings = readings;
        this.items = items;
        this.revisions = revisions;
        this.documents = documents;
        this.expenses = expenses;
        this.shares = shares;
        this.access = access;
        this.users = users;
        this.images = images;
        this.fipe = fipe;
        this.rateLimiter = rateLimiter;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** Seus (inclusive vendidos/arquivados) e os ativos compartilhados com você. */
    @Transactional(readOnly = true)
    public VehicleList list(UUID userId) {
        List<Vehicle> owned = vehicles.findByUserIdOrderByArchivedAtAscCreatedAtAsc(userId);
        Map<UUID, Grant> grants = access.grants(userId);
        List<Vehicle> shared = grants.isEmpty() ? List.of() : vehicles.findByIdInOrderByCreatedAtAsc(grants.keySet()).stream()
                .filter(v -> !v.getUserId().equals(userId) && v.getArchivedAt() == null).toList();
        List<Vehicle> all = new ArrayList<>(owned);
        all.addAll(shared);
        Map<UUID, Loaded> loaded = load(all);
        Map<UUID, User> owners = owners(all);
        LocalDate today = today();
        return new VehicleList(
                owned.stream().map(v -> card(loaded.get(v.getId()), owners, userId, VehicleRole.OWNER, null, today)).toList(),
                shared.stream().map(v -> {
                    Grant grant = grants.get(v.getId());
                    return card(loaded.get(v.getId()), owners, userId, grant.role(), grant.groupName(), today);
                }).toList());
    }

    @Transactional(readOnly = true)
    public VehicleDetail get(UUID userId, UUID id) {
        Access found = access.require(id, userId, VehicleRole.VIEW);
        Vehicle v = found.vehicle();
        Loaded data = new Loaded(v, readings.findByVehicleIdOrderByReadOnAscKmAsc(id),
                items.findByVehicleIdOrderByPositionAscCreatedAtAsc(id),
                revisions.findByVehicleIdOrderByDueKmAscDueOnAscCreatedAtAsc(id), documents.findByVehicleIdOrderByDueOnAsc(id));
        LocalDate today = today();
        Pace pace = VehicleViews.pace(v, data.readings(), today);
        List<Upcoming> upcoming = VehicleViews.upcoming(data, pace, today);
        String via = found.role() == VehicleRole.OWNER ? null : access.grants(userId).getOrDefault(id, new Grant(found.role(), null))
                .groupName();
        VehicleCard card = VehicleViews.card(data, pace, upcoming, users.findById(v.getUserId()).map(UserSummary::from).orElse(null),
                found.role() == VehicleRole.OWNER, found.role(), via);
        FipeInfo fipeInfo = v.getFipeKind() == null ? null : new FipeInfo(v.getFipeKind(), v.getFipeBrand(), v.getFipeModel(),
                v.getFipeYear(), v.getFipeCode(), v.getFipeValue(), v.getFipeMonth(), v.getFipeCheckedAt());
        return new VehicleDetail(card, v.getTankLiters(), v.getNotes(), fipeInfo,
                data.items().stream().map(i -> VehicleViews.item(i, pace, today)).toList(),
                data.revisions().stream().map(r -> VehicleViews.revision(r, pace, today)).toList(),
                data.documents().stream().map(d -> VehicleViews.document(d, today)).toList(), upcoming);
    }

    /** Vencimentos perto ou vencidos dos veículos ativos que a pessoa vê (até 5, mais urgentes primeiro). */
    @Transactional(readOnly = true)
    public Summary summary(UUID userId) {
        VehicleList list = list(userId);
        List<VehicleCard> active = new ArrayList<>(list.owned().stream().filter(c -> c.archivedAt() == null).toList());
        active.addAll(list.shared());
        LocalDate today = today();
        Map<UUID, Loaded> loaded = load(vehicles.findAllById(active.stream().map(VehicleCard::id).toList()));
        List<SummaryItem> upcoming = new ArrayList<>();
        for (VehicleCard card : active) {
            Loaded data = loaded.get(card.id());
            Pace pace = VehicleViews.pace(data.vehicle(), data.readings(), today);
            VehicleViews.upcoming(data, pace, today).stream().filter(u -> u.due().stage() != Stage.OK)
                    .forEach(u -> upcoming.add(new SummaryItem(card.id(), card.name(), card.type(), u)));
        }
        upcoming.sort(Comparator.comparing((SummaryItem s) -> s.upcoming().due().stage(), Comparator.reverseOrder())
                .thenComparing(s -> s.upcoming().due().expectedOn(), Comparator.nullsLast(Comparator.naturalOrder())));
        return new Summary(active.size(), upcoming.stream().limit(5).toList());
    }

    /** Grava e, com referência da FIPE, já busca o valor (fora da transação). */
    public VehicleDetail create(UUID userId, VehicleRequest request) {
        UUID id = transaction.execute(status -> {
            if (vehicles.countByUserId(userId) >= MAX_VEHICLES) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_VEHICLES + " veículos.");
            }
            Vehicle vehicle = new Vehicle(userId);
            apply(vehicle, request);
            vehicles.save(vehicle);
            LocalDate today = today();
            if (request.odometer() != null && request.odometer() > 0) {
                readings.save(new VehicleReading(vehicle.getId(), userId, null, request.odometer(), today));
                vehicle.setOdometer(request.odometer(), today);
            }
            if (Boolean.TRUE.equals(request.template())) {
                List<Template> template = VehicleTemplates.of(request.type());
                for (int i = 0; i < template.size(); i++) {
                    VehicleItem item = new VehicleItem(vehicle.getId(), i);
                    item.set(template.get(i).name(), template.get(i).intervalKm(), template.get(i).intervalMonths(), true);
                    items.save(item);
                }
            }
            return vehicle.getId();
        });
        if (request.fipe() != null) refreshFipeQuietly(id);
        return transaction.execute(status -> get(userId, id));
    }

    public VehicleDetail update(UUID userId, UUID id, VehicleRequest request) {
        boolean fetch = Boolean.TRUE.equals(transaction.execute(status -> {
            Vehicle vehicle = access.require(id, userId, VehicleRole.EDIT).vehicle();
            apply(vehicle, request);
            return vehicle.hasFipe() && vehicle.getFipeValue() == null;
        }));
        if (fetch) refreshFipeQuietly(id);
        return transaction.execute(status -> get(userId, id));
    }

    @Transactional
    public VehicleDetail archive(UUID userId, UUID id, boolean archived) {
        Vehicle vehicle = access.require(id, userId, VehicleRole.OWNER).vehicle();
        vehicle.setArchivedAt(archived ? OffsetDateTime.now(clock) : null);
        return get(userId, id);
    }

    /** Some tudo junto (o banco apaga em cascata); os lançamentos já feitos no financeiro ficam. */
    @Transactional
    public void delete(UUID userId, UUID id) {
        Vehicle vehicle = access.require(id, userId, VehicleRole.OWNER).vehicle();
        images.deleteAfterCommit(vehicle.getImageKey());
        expenses.imageKeysOfVehicle(id).forEach(images::deleteAfterCommit);
        vehicles.delete(vehicle);
    }

    @Transactional
    public VehicleDetail setImage(UUID userId, UUID id, byte[] bytes) {
        Vehicle vehicle = access.require(id, userId, VehicleRole.EDIT).vehicle();
        String previous = vehicle.getImageKey();
        vehicle.setImageKey(images.savePhoto(bytes));
        images.deleteAfterCommit(previous);
        return get(userId, id);
    }

    @Transactional
    public VehicleDetail removeImage(UUID userId, UUID id) {
        Vehicle vehicle = access.require(id, userId, VehicleRole.EDIT).vehicle();
        images.deleteAfterCommit(vehicle.getImageKey());
        vehicle.setImageKey(null);
        return get(userId, id);
    }

    @Transactional(readOnly = true)
    public ImageFile image(UUID userId, UUID id) {
        Vehicle vehicle = access.require(id, userId, VehicleRole.VIEW).vehicle();
        if (vehicle.getImageKey() == null) throw new NotFoundException("Sem foto.");
        return images.file(vehicle.getImageKey());
    }

    /** Busca o valor da FIPE agora (quem edita; uma vez a cada 10 min por veículo). */
    public VehicleDetail refreshFipe(UUID userId, UUID id) {
        Vehicle vehicle = transaction.execute(status -> access.require(id, userId, VehicleRole.EDIT).vehicle());
        if (!vehicle.hasFipe()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha o modelo na Tabela FIPE em Editar.");
        if (!rateLimiter.tryAcquire("vehicle-fipe:" + id, 1, FIPE_REFRESH)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Consultado há pouco. Tente de novo em alguns minutos.");
        }
        try {
            savePrice(id, fipe.price(vehicle.getFipeKind(), vehicle.getFipeBrand(), vehicle.getFipeModel(), vehicle.getFipeYear()));
        } catch (FipeException e) {
            throw unavailable(e);
        }
        return transaction.execute(status -> get(userId, id));
    }

    public List<FipeOption> fipeBrands(UUID userId, FipeKind kind) {
        lookup(userId);
        try {
            return fipe.brands(kind).stream().map(o -> new FipeOption(o.code(), o.name())).toList();
        } catch (FipeException e) {
            throw unavailable(e);
        }
    }

    public List<FipeOption> fipeModels(UUID userId, FipeKind kind, String brand) {
        lookup(userId);
        try {
            return fipe.models(kind, brand).stream().map(o -> new FipeOption(o.code(), o.name())).toList();
        } catch (FipeException e) {
            throw unavailable(e);
        }
    }

    public List<FipeOption> fipeYears(UUID userId, FipeKind kind, String brand, String model) {
        lookup(userId);
        try {
            return fipe.years(kind, brand, model).stream().map(o -> new FipeOption(o.code(), o.name())).toList();
        } catch (FipeException e) {
            throw unavailable(e);
        }
    }

    public FipePrice fipePrice(UUID userId, FipeKind kind, String brand, String model, String year) {
        lookup(userId);
        try {
            Price p = fipe.price(kind, brand, model, year).orElseThrow(() -> new NotFoundException("Modelo não encontrado na FIPE."));
            return new FipePrice(p.code(), p.value(), p.brand(), p.model(), p.modelYear(), p.fuel(), p.month());
        } catch (FipeException e) {
            throw unavailable(e);
        }
    }

    public List<VehicleDtos.Template> templates(VehicleType type) {
        return VehicleTemplates.of(type).stream()
                .map(t -> new VehicleDtos.Template(t.name(), t.intervalKm(), t.intervalMonths())).toList();
    }

    /** Valor da FIPE dos veículos ativos com referência, lido há mais de 20 dias (o job roda todo dia). */
    public int refreshFipeDue() {
        List<UUID> due = transaction.execute(status -> vehicles.fipeDue(OffsetDateTime.now(clock).minusDays(20),
                PageRequest.of(0, FIPE_BATCH)).stream().map(Vehicle::getId).toList());
        int done = 0;
        for (UUID id : due) {
            if (!refreshFipeQuietly(id)) break;
            done++;
        }
        return done;
    }

    @Transactional(readOnly = true)
    public List<VehicleData> exportData(UUID userId) {
        List<VehicleData> list = new ArrayList<>();
        for (Vehicle v : vehicles.findByUserIdOrderByArchivedAtAscCreatedAtAsc(userId)) {
            UUID id = v.getId();
            list.add(new VehicleData(v.getType(), v.getName(), v.getBrand(), v.getModel(), v.getModelYear(), v.getPlate(), v.getFuel(),
                    v.getOdometer(), v.getFipeCode(), v.getFipeValue(), v.getArchivedAt(),
                    readings.findByVehicleIdOrderByReadOnAscKmAsc(id).stream().map(r -> new ReadingData(r.getReadOn(), r.getKm())).toList(),
                    expenses.findByVehicleIdOrderBySpentOnAscCreatedAtAsc(id).stream().map(e -> new ExpenseData(e.getKind(),
                            e.getSpentOn(), e.getAmount(), e.getDescription(), e.getOdometer(), e.getLiters(), e.getFuel(), e.isFullTank(),
                            e.getStation(), e.getNotes(), userId.equals(e.getUserId()))).toList(),
                    items.findByVehicleIdOrderByPositionAscCreatedAtAsc(id).stream().map(i -> new ItemData(i.getName(), i.getIntervalKm(),
                            i.getIntervalMonths(), i.getLastKm(), i.getLastOn())).toList(),
                    revisions.findByVehicleIdOrderByDueKmAscDueOnAscCreatedAtAsc(id).stream().map(r -> new RevisionData(r.getName(),
                            r.getDueKm(), r.getDueOn(), r.getDoneOn(), r.getDoneKm(), r.getShop())).toList(),
                    documents.findByVehicleIdOrderByDueOnAsc(id).stream().map(d -> new DocumentData(d.getKind(), d.getName(),
                            d.getDueOn(), d.getAmount())).toList()));
        }
        return list;
    }

    /** Conta excluída: as fotos dos veículos dela e os comprovantes lançados neles saem do disco. */
    public void deleteUserImagesAfterCommit(UUID userId) {
        vehicles.imageKeysOf(userId).forEach(images::deleteAfterCommit);
        expenses.imageKeysOfOwner(userId).forEach(images::deleteAfterCommit);
    }

    /** Quem sai do grupo deixa de compartilhar os veículos dele ali. */
    @EventListener
    public void onMemberRemoved(GroupMemberRemovedEvent event) {
        shares.deleteGroupSharesOf(event.userId(), event.groupId());
    }

    /** Leituras, itens, revisões e documentos dos veículos, numa consulta por tabela. */
    private Map<UUID, Loaded> load(Collection<Vehicle> list) {
        if (list.isEmpty()) return Map.of();
        List<UUID> ids = list.stream().map(Vehicle::getId).toList();
        Map<UUID, List<VehicleReading>> byReading = readings.findByVehicleIdInOrderByReadOnAscKmAsc(ids).stream()
                .collect(Collectors.groupingBy(VehicleReading::getVehicleId));
        Map<UUID, List<VehicleItem>> byItem = items.findByVehicleIdIn(ids).stream()
                .collect(Collectors.groupingBy(VehicleItem::getVehicleId));
        Map<UUID, List<VehicleRevision>> byRevision = revisions.findByVehicleIdIn(ids).stream()
                .collect(Collectors.groupingBy(VehicleRevision::getVehicleId));
        Map<UUID, List<VehicleDocument>> byDocument = documents.findByVehicleIdIn(ids).stream()
                .collect(Collectors.groupingBy(VehicleDocument::getVehicleId));
        return list.stream().collect(Collectors.toMap(Vehicle::getId, v -> new Loaded(v,
                byReading.getOrDefault(v.getId(), List.of()), byItem.getOrDefault(v.getId(), List.of()),
                byRevision.getOrDefault(v.getId(), List.of()), byDocument.getOrDefault(v.getId(), List.of()))));
    }

    private Map<UUID, User> owners(Collection<Vehicle> list) {
        return users.findAllById(list.stream().map(Vehicle::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private VehicleCard card(Loaded data, Map<UUID, User> owners, UUID userId, VehicleRole role, String via, LocalDate today) {
        Pace pace = VehicleViews.pace(data.vehicle(), data.readings(), today);
        User owner = owners.get(data.vehicle().getUserId());
        return VehicleViews.card(data, pace, VehicleViews.upcoming(data, pace, today), owner == null ? null : UserSummary.from(owner),
                data.vehicle().getUserId().equals(userId), role, via);
    }

    private void apply(Vehicle vehicle, VehicleRequest r) {
        String plate = clean(r.plate());
        vehicle.set(r.type(), r.name().trim(), clean(r.brand()), clean(r.model()), r.modelYear(),
                plate == null ? null : plate.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9-]", ""), clean(r.color()), r.fuel(),
                r.tankLiters(), clean(r.notes()));
        FipeRef ref = r.fipe();
        if (ref != null && r.type().fipe() != ref.kind()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Tabela FIPE de outro tipo.");
        if (ref == null) vehicle.setFipeRef(null, null, null, null);
        else vehicle.setFipeRef(ref.kind(), ref.brand(), ref.model(), ref.year());
    }

    /** Falha da FIPE vira só log; devolve falso se a FIPE estiver fora (o job para). */
    private boolean refreshFipeQuietly(UUID id) {
        Vehicle vehicle = transaction.execute(status -> vehicles.findById(id).orElse(null));
        if (vehicle == null || !vehicle.hasFipe()) return true;
        try {
            savePrice(id, fipe.price(vehicle.getFipeKind(), vehicle.getFipeBrand(), vehicle.getFipeModel(), vehicle.getFipeYear()));
            return true;
        } catch (FipeException e) {
            log.info("FIPE não respondeu para o veículo {}: {}", id, e.getMessage());
            return false;
        }
    }

    private void savePrice(UUID id, Optional<Price> price) {
        transaction.executeWithoutResult(status -> vehicles.findById(id).ifPresent(v -> {
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (price.isPresent()) v.setFipeValue(price.get().code(), price.get().value(), price.get().month(), now);
            else v.setFipeValue(null, null, null, now);
        }));
    }

    private void lookup(UUID userId) {
        if (!rateLimiter.tryAcquire("vehicle-fipe-lookup:" + userId, FIPE_LOOKUPS_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitas consultas na FIPE. Espere um pouco.");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static BusinessException unavailable(FipeException e) {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Tabela FIPE indisponível agora (" + e.getMessage() + ")");
    }

    private static String clean(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
