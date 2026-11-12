package com.lifepedia.api.delivery;

import com.lifepedia.api.delivery.DeliveryDtos.AddressData;
import com.lifepedia.api.delivery.DeliveryDtos.AddressRequest;
import com.lifepedia.api.delivery.DeliveryDtos.AddressResponse;
import com.lifepedia.api.delivery.DeliveryDtos.AddressShort;
import com.lifepedia.api.delivery.DeliveryDtos.Carrier;
import com.lifepedia.api.delivery.DeliveryDtos.CepResponse;
import com.lifepedia.api.delivery.DeliveryDtos.DeliveriesExport;
import com.lifepedia.api.delivery.DeliveryDtos.DeliveryData;
import com.lifepedia.api.delivery.DeliveryDtos.DeliveryRequest;
import com.lifepedia.api.delivery.DeliveryDtos.DeliveryResponse;
import com.lifepedia.api.delivery.DeliveryDtos.DeliveryUpdate;
import com.lifepedia.api.delivery.DeliveryDtos.GroupRef;
import com.lifepedia.api.delivery.DeliveryDtos.Summary;
import com.lifepedia.api.delivery.TrackSnapshot.TrackEvent;
import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRemovedEvent;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.group.UserGroup;
import com.lifepedia.api.group.UserGroupRepository;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.shared.image.ImageFile;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Entregas da pessoa (e as compartilhadas com os grupos dela) e endereços de entrega. */
@Service
public class DeliveryService {

    static final int MAX_ACTIVE = 60;
    static final int MAX_ADDRESSES = 20;
    static final int CREATES_PER_DAY = 15;
    private static final Pattern CODE = Pattern.compile("[A-Z0-9-]{6,40}");
    private static final Duration REFRESH = Duration.ofMinutes(5);
    private static final TypeReference<List<TrackEvent>> EVENTS = new TypeReference<>() {
    };

    private final DeliveryRepository deliveries;
    private final DeliveryAddressRepository addresses;
    private final GroupMemberRepository members;
    private final UserGroupRepository groups;
    private final GroupAccess groupAccess;
    private final UserRepository users;
    private final DeliveryImageStore images;
    private final DeliverySyncService sync;
    private final Track17Client track17;
    private final CepClient cepClient;
    private final RateLimiter rateLimiter;
    private final TransactionTemplate transaction;
    private final JsonMapper json;
    private final Clock clock;

    public DeliveryService(DeliveryRepository deliveries, DeliveryAddressRepository addresses, GroupMemberRepository members,
                           UserGroupRepository groups, GroupAccess groupAccess, UserRepository users, DeliveryImageStore images,
                           DeliverySyncService sync, Track17Client track17, CepClient cepClient, RateLimiter rateLimiter,
                           PlatformTransactionManager transactions, JsonMapper json, Clock clock) {
        this.deliveries = deliveries;
        this.addresses = addresses;
        this.members = members;
        this.groups = groups;
        this.groupAccess = groupAccess;
        this.users = users;
        this.images = images;
        this.sync = sync;
        this.track17 = track17;
        this.cepClient = cepClient;
        this.rateLimiter = rateLimiter;
        this.transaction = new TransactionTemplate(transactions);
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> list(UUID userId, boolean archived) {
        return responses(userId, deliveries.visible(userId, groupIds(userId), archived), false);
    }

    @Transactional(readOnly = true)
    public DeliveryResponse get(UUID userId, UUID id) {
        return responses(userId, List.of(visible(userId, id)), true).getFirst();
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID userId) {
        List<DeliveryResponse> active = responses(userId, deliveries.visible(userId, groupIds(userId), false), false);
        return new Summary(active.size(), active.stream().filter(DeliveryResponse::arrivesToday).toList(),
                active.stream().filter(DeliveryResponse::problem).count(),
                active.stream().filter(DeliveryResponse::taxPending).count());
    }

    /** Grava e já busca o rastreio (fora da transação). */
    public DeliveryResponse create(UUID userId, DeliveryRequest request) {
        String code = normalize(request.trackingCode());
        if (!rateLimiter.tryAcquire("delivery-create:" + userId, CREATES_PER_DAY, Duration.ofDays(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Limite de " + CREATES_PER_DAY + " entregas novas por dia.");
        }
        UUID id = transaction.execute(status -> {
            if (deliveries.existsByUserIdAndTrackingCode(userId, code)) {
                throw new BusinessException(HttpStatus.CONFLICT, "Você já acompanha esse código.");
            }
            if (deliveries.countByUserIdAndArchivedAtIsNull(userId) >= MAX_ACTIVE) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Limite de " + MAX_ACTIVE + " entregas em andamento. Arquive as antigas.");
            }
            Delivery delivery = new Delivery(userId, code);
            delivery.set(request.name().trim(), requireAddress(userId, request.addressId()), requireGroup(userId, request.groupId()));
            return deliveries.save(delivery).getId();
        });
        sync.sync(List.of(id));
        return transaction.execute(status -> get(userId, id));
    }

    @Transactional
    public DeliveryResponse update(UUID userId, UUID id, DeliveryUpdate request) {
        Delivery delivery = own(userId, id);
        delivery.set(request.name().trim(), requireAddress(userId, request.addressId()), requireGroup(userId, request.groupId()));
        return get(userId, id);
    }

    @Transactional
    public DeliveryResponse archive(UUID userId, UUID id, boolean archived) {
        Delivery delivery = own(userId, id);
        delivery.setArchivedAt(archived ? OffsetDateTime.now(clock) : null);
        return get(userId, id);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Delivery delivery = own(userId, id);
        images.deleteAfterCommit(delivery.getImageKey());
        deliveries.delete(delivery);
    }

    /** Atualizar agora: quem vê a entrega, no máximo a cada 5 min. */
    public DeliveryResponse refresh(UUID userId, UUID id) {
        if (!track17.enabled()) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Rastreio automático desligado no servidor.");
        Delivery delivery = transaction.execute(status -> visible(userId, id));
        OffsetDateTime last = delivery.getLastSyncAt();
        if (last != null && last.plus(REFRESH).isAfter(OffsetDateTime.now(clock))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Atualizado há pouco. Tente de novo em alguns minutos.");
        }
        sync.sync(List.of(id));
        return transaction.execute(status -> get(userId, id));
    }

    @Transactional
    public DeliveryResponse setImage(UUID userId, UUID id, byte[] bytes) {
        Delivery delivery = own(userId, id);
        String previous = delivery.getImageKey();
        delivery.setImageKey(images.save(bytes));
        images.deleteAfterCommit(previous);
        return get(userId, id);
    }

    @Transactional
    public DeliveryResponse removeImage(UUID userId, UUID id) {
        Delivery delivery = own(userId, id);
        images.deleteAfterCommit(delivery.getImageKey());
        delivery.setImageKey(null);
        return get(userId, id);
    }

    @Transactional(readOnly = true)
    public ImageFile image(UUID userId, UUID id) {
        Delivery delivery = visible(userId, id);
        if (delivery.getImageKey() == null) throw new NotFoundException("Sem foto.");
        return images.file(delivery.getImageKey());
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> addresses(UUID userId) {
        return addresses.listOf(userId).stream().map(AddressResponse::of).toList();
    }

    @Transactional
    public AddressResponse createAddress(UUID userId, AddressRequest request) {
        long count = addresses.countByUserId(userId);
        if (count >= MAX_ADDRESSES) throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_ADDRESSES + " endereços.");
        DeliveryAddress address = new DeliveryAddress(userId);
        fill(address, request);
        address.setDefaultAddress(count == 0 || Boolean.TRUE.equals(request.defaultAddress()));
        addresses.save(address);
        if (address.isDefaultAddress()) addresses.clearDefault(userId, address.getId());
        return AddressResponse.of(address);
    }

    @Transactional
    public AddressResponse updateAddress(UUID userId, UUID id, AddressRequest request) {
        DeliveryAddress address = address(userId, id);
        fill(address, request);
        if (Boolean.TRUE.equals(request.defaultAddress()) && !address.isDefaultAddress()) {
            address.setDefaultAddress(true);
            addresses.clearDefault(userId, address.getId());
        }
        return AddressResponse.of(address);
    }

    /** As entregas que usavam ficam sem endereço; o padrão passa para o próximo. */
    @Transactional
    public void deleteAddress(UUID userId, UUID id) {
        DeliveryAddress address = address(userId, id);
        addresses.delete(address);
        addresses.flush();
        if (address.isDefaultAddress()) {
            addresses.listOf(userId).stream().findFirst().ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    public CepResponse cep(UUID userId, String raw) {
        String cep = raw == null ? "" : raw.replaceAll("\\D", "");
        if (cep.length() != 8) throw new BusinessException(HttpStatus.BAD_REQUEST, "CEP inválido.");
        if (!rateLimiter.tryAcquire("delivery-cep:" + userId, 30, Duration.ofMinutes(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitas buscas de CEP. Espere um pouco.");
        }
        return cepClient.find(cep)
                .map(a -> new CepResponse(a.cep(), a.street(), a.district(), a.city(), a.state()))
                .orElseThrow(() -> new NotFoundException("CEP não encontrado. Preencha o endereço à mão."));
    }

    @Transactional(readOnly = true)
    public DeliveriesExport exportData(UUID userId) {
        Map<UUID, DeliveryAddress> byId = addresses.listOf(userId).stream()
                .collect(Collectors.toMap(DeliveryAddress::getId, Function.identity()));
        return new DeliveriesExport(
                byId.values().stream().map(a -> new AddressData(a.getLabel(), a.getRecipient(), a.getCep(), a.getStreet(),
                        a.getNumber(), a.getComplement(), a.getDistrict(), a.getCity(), a.getState(), a.isDefaultAddress())).toList(),
                deliveries.findByUserIdOrderByCreatedAtDesc(userId).stream().map(d -> new DeliveryData(d.getName(),
                        d.getTrackingCode(), d.getStatus(), d.getCarrierName(),
                        d.getAddressId() == null || !byId.containsKey(d.getAddressId()) ? null : byId.get(d.getAddressId()).getLabel(),
                        d.getDeliveredAt(), d.getArchivedAt(), d.getCreatedAt())).toList());
    }

    /** Conta excluída: as fotos saem do disco (o banco apaga as linhas em cascata). */
    public void deleteUserImagesAfterCommit(UUID userId) {
        deliveries.imageKeysOf(userId).forEach(images::deleteAfterCommit);
    }

    /** Quem sai do grupo deixa de compartilhar as entregas dele ali. */
    @EventListener
    public void onMemberRemoved(GroupMemberRemovedEvent event) {
        deliveries.findByUserIdAndGroupId(event.userId(), event.groupId()).forEach(d -> d.setGroupId(null));
    }

    private List<DeliveryResponse> responses(UUID userId, List<Delivery> items, boolean withEvents) {
        if (items.isEmpty()) return List.of();
        Map<UUID, DeliveryAddress> addressById = addresses.findByIdIn(ids(items, Delivery::getAddressId)).stream()
                .collect(Collectors.toMap(DeliveryAddress::getId, Function.identity()));
        Map<UUID, UserGroup> groupById = groups.findAllById(ids(items, Delivery::getGroupId)).stream()
                .collect(Collectors.toMap(UserGroup::getId, Function.identity()));
        Map<UUID, User> ownerById = users.findAllById(ids(items, Delivery::getUserId)).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate today = LocalDate.now(clock);
        return items.stream().map(d -> {
            boolean mine = d.getUserId().equals(userId);
            DeliveryAddress address = addressById.get(d.getAddressId());
            List<TrackEvent> events = events(d);
            boolean delivered = d.getStatus() == DeliveryStatus.DELIVERED;
            OffsetDateTime first = events.stream().map(TrackEvent::at).filter(Objects::nonNull).min(OffsetDateTime::compareTo).orElse(null);
            Integer inTransit = first == null ? null
                    : (int) ChronoUnit.DAYS.between(first, delivered && d.getDeliveredAt() != null ? d.getDeliveredAt() : now);
            Integer stalled = delivered || d.getLatestAt() == null ? null : (int) ChronoUnit.DAYS.between(d.getLatestAt(), now);
            boolean arrivesToday = !delivered && (d.getStatus() == DeliveryStatus.OUT_FOR_DELIVERY || today.equals(d.getEstimatedTo()));
            UserGroup group = groupById.get(d.getGroupId());
            User owner = ownerById.get(d.getUserId());
            return new DeliveryResponse(d.getId(), d.getName(), d.getTrackingCode(), d.getStatus(), d.getStatus().stage(),
                    d.getStatus().problem(), d.getSubStatus(), d.isTaxPending(),
                    d.getCarrierName() == null ? null : new Carrier(d.getCarrierName(), d.getCarrierUrl()), d.getLatestAt(),
                    d.getLatestText(), d.getLatestLocation(), d.getOrigin(), d.getEstimatedFrom(), d.getEstimatedTo(),
                    d.getDeliveredAt(), inTransit, stalled, arrivesToday, d.imageUrl(),
                    mine && address != null ? AddressResponse.of(address) : null,
                    address == null ? null : new AddressShort(address.getLabel(), address.getCity(), address.getState()),
                    group == null ? null : new GroupRef(group.getId(), group.getName()),
                    owner == null ? null : UserSummary.from(owner), mine, d.getArchivedAt(), d.getLastSyncAt(),
                    d.getSyncError(), track17.enabled(), d.getCreatedAt(), withEvents ? events : null);
        }).toList();
    }

    private List<TrackEvent> events(Delivery d) {
        if (d.getEvents() == null) return List.of();
        try {
            return json.readValue(d.getEvents(), EVENTS);
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private Collection<UUID> groupIds(UUID userId) {
        List<UUID> ids = members.findByUserId(userId).stream().map(GroupMember::getGroupId).toList();
        return ids.isEmpty() ? List.of(new UUID(0, 0)) : ids;
    }

    private Delivery visible(UUID userId, UUID id) {
        Delivery delivery = deliveries.findById(id).orElseThrow(() -> new NotFoundException("Entrega não encontrada."));
        if (delivery.getUserId().equals(userId)) return delivery;
        if (delivery.getGroupId() != null && members.existsByGroupIdAndUserId(delivery.getGroupId(), userId)) return delivery;
        throw new NotFoundException("Entrega não encontrada.");
    }

    private Delivery own(UUID userId, UUID id) {
        Delivery delivery = visible(userId, id);
        if (!delivery.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Só quem cadastrou a entrega pode mudar.");
        }
        return delivery;
    }

    private DeliveryAddress address(UUID userId, UUID id) {
        return addresses.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Endereço não encontrado."));
    }

    private UUID requireAddress(UUID userId, UUID addressId) {
        if (addressId == null) return null;
        if (addresses.findByIdAndUserId(addressId, userId).isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Endereço inválido.");
        }
        return addressId;
    }

    private UUID requireGroup(UUID userId, UUID groupId) {
        if (groupId == null) return null;
        groupAccess.requireMember(groupId, userId);
        return groupId;
    }

    private static void fill(DeliveryAddress address, AddressRequest r) {
        address.set(r.label().trim(), blank(r.recipient()), r.cep().replace("-", ""), r.street().trim(), blank(r.number()),
                blank(r.complement()), blank(r.district()), r.city().trim(), r.state().trim().toUpperCase(Locale.ROOT));
    }

    private static String normalize(String raw) {
        String code = raw.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Código de rastreio inválido: use letras e números (6 a 40).");
        }
        return code;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Set<UUID> ids(List<Delivery> items, Function<Delivery, UUID> getter) {
        return items.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
