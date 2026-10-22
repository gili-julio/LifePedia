package com.lifepedia.api.planning;

import com.lifepedia.api.finance.group.GroupFinanceService;
import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupDeletedEvent;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRemovedEvent;
import com.lifepedia.api.planning.PlanningDtos.AcquireRequest;
import com.lifepedia.api.planning.PlanningDtos.PlanItemRequest;
import com.lifepedia.api.planning.PlanningDtos.PlanItemResponse;
import com.lifepedia.api.planning.PlanningDtos.ScenarioRequest;
import com.lifepedia.api.planning.PlanningDtos.ScenarioResponse;
import com.lifepedia.api.planning.PlanningDtos.Settings;
import com.lifepedia.api.planning.PlanningDtos.SettingsRequest;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.image.ImageFile;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.user.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Itens, cenários e base do planejamento do grupo. Qualquer membro cadastra; editar item: quem cadastrou ou admin. */
@Service
public class PlanningService {

    static final int MAX_ITEMS = 100;
    static final int MAX_SCENARIOS = 20;
    private static final int MAX_YEARS_AHEAD = 10;
    private static final Set<Integer> AVERAGE_MONTHS = Set.of(0, 3, 6, 12);

    private final PlanItemRepository items;
    private final PlanScenarioRepository scenarios;
    private final PlanSettingsRepository settings;
    private final PlanImageStore images;
    private final GroupFinanceService groupFinance;
    private final UserRepository users;
    private final GroupAccess access;

    public PlanningService(PlanItemRepository items, PlanScenarioRepository scenarios, PlanSettingsRepository settings,
                           PlanImageStore images, GroupFinanceService groupFinance, UserRepository users,
                           GroupAccess access) {
        this.items = items;
        this.scenarios = scenarios;
        this.settings = settings;
        this.images = images;
        this.groupFinance = groupFinance;
        this.users = users;
        this.access = access;
    }

    @Transactional
    public PlanItemResponse create(UUID userId, UUID groupId, PlanItemRequest request) {
        GroupMember me = access.requireMember(groupId, userId);
        if (items.countByGroupId(groupId) >= MAX_ITEMS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O planejamento tem o limite de " + MAX_ITEMS + " itens.");
        }
        PlanItem item = new PlanItem(groupId, userId, items.maxPosition(groupId) + 1);
        apply(item, request);
        if (request.active() != null) item.setActive(request.active());
        items.save(item);
        return responses(List.of(item), me).getFirst();
    }

    @Transactional
    public PlanItemResponse update(UUID userId, UUID groupId, UUID id, PlanItemRequest request) {
        GroupMember me = access.requireMember(groupId, userId);
        PlanItem item = find(groupId, id);
        requireCanEdit(me, item);
        apply(item, request);
        if (request.active() != null) item.setActive(request.active());
        return responses(List.of(item), me).getFirst();
    }

    @Transactional
    public void delete(UUID userId, UUID groupId, UUID id) {
        GroupMember me = access.requireMember(groupId, userId);
        PlanItem item = find(groupId, id);
        requireCanEdit(me, item);
        images.deleteAfterCommit(item.getImageKey());
        items.delete(item);
    }

    /** Liga/desliga na simulação (qualquer membro). */
    @Transactional
    public void setActive(UUID userId, UUID groupId, UUID id, boolean active) {
        access.requireMember(groupId, userId);
        find(groupId, id).setActive(active);
    }

    /** Nova prioridade: os listados primeiro, na ordem dada; os demais depois, na ordem atual. */
    @Transactional
    public void reorder(UUID userId, UUID groupId, List<UUID> orderedIds) {
        access.requireMember(groupId, userId);
        Set<UUID> ids = new LinkedHashSet<>(orderedIds);
        List<PlanItem> all = items.findByGroupIdOrderByPositionAscCreatedAtAsc(groupId);
        Map<UUID, PlanItem> byId = all.stream().collect(Collectors.toMap(PlanItem::getId, Function.identity()));
        if (!byId.keySet().containsAll(ids)) throw new NotFoundException("Item não encontrado.");
        List<PlanItem> ordered = new ArrayList<>(ids.stream().map(byId::get).toList());
        all.stream().filter(i -> !ids.contains(i.getId())).forEach(ordered::add);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(i);
    }

    /** Marca como comprado/feito e, se pedido, lança a despesa no grupo em nome de quem marcou. */
    @Transactional
    public void acquire(UUID userId, UUID groupId, UUID id, AcquireRequest request) {
        GroupMember me = access.requireMember(groupId, userId);
        PlanItem item = find(groupId, id);
        requireCanEdit(me, item);
        if (item.isAcquired()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Item já marcado como adquirido.");
        if (request.date().isAfter(LocalDate.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "A data não pode ser no futuro.");
        }
        if (request.launchOrFalse()) {
            if (request.categoryId() == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha a categoria.");
            groupFinance.addExpense(userId, groupId, item.getName(), request.categoryId(), request.amount(),
                    request.date(), item.getInstallments(), item.getNotes());
        }
        item.acquire(request.date(), request.amount());
    }

    /** Volta para o planejamento (lançamentos já feitos continuam no grupo). */
    @Transactional
    public void unacquire(UUID userId, UUID groupId, UUID id) {
        GroupMember me = access.requireMember(groupId, userId);
        PlanItem item = find(groupId, id);
        requireCanEdit(me, item);
        item.acquire(null, null);
    }

    @Transactional(readOnly = true)
    public ImageFile image(UUID userId, UUID groupId, UUID id) {
        access.requireMember(groupId, userId);
        PlanItem item = find(groupId, id);
        if (item.getImageKey() == null) throw new NotFoundException("O item não tem imagem.");
        return images.file(item.getImageKey());
    }

    @Transactional
    public Settings updateSettings(UUID userId, UUID groupId, SettingsRequest request) {
        access.requireMember(groupId, userId);
        if (!AVERAGE_MONTHS.contains(request.averageMonths())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Use média de 0, 3, 6 ou 12 meses.");
        }
        PlanSettings current = settings.findByGroupId(groupId).orElseGet(() -> settings.save(new PlanSettings(groupId)));
        current.set(request.reserve(), request.averageMonths(), request.manualBalance());
        return settingsOf(current);
    }

    @Transactional(readOnly = true)
    public List<ScenarioResponse> scenarios(UUID userId, UUID groupId) {
        GroupMember me = access.requireMember(groupId, userId);
        List<PlanScenario> list = scenarios.findByGroupIdOrderByCreatedAtAsc(groupId);
        Map<UUID, User> byId = usersOf(list.stream().map(PlanScenario::getUserId).toList());
        return list.stream().map(s -> scenarioResponse(s, byId, me)).toList();
    }

    @Transactional
    public ScenarioResponse createScenario(UUID userId, UUID groupId, ScenarioRequest request) {
        GroupMember me = access.requireMember(groupId, userId);
        if (scenarios.countByGroupId(groupId) >= MAX_SCENARIOS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O planejamento tem o limite de " + MAX_SCENARIOS + " cenários.");
        }
        PlanScenario scenario = new PlanScenario(groupId, userId);
        scenario.set(request.name().trim(), scenarioItems(groupId, request.itemIds()));
        scenarios.save(scenario);
        return scenarioResponse(scenario, usersOf(List.of(userId)), me);
    }

    /** Qualquer membro ajusta os itens de um cenário (é um rascunho compartilhado). */
    @Transactional
    public ScenarioResponse updateScenario(UUID userId, UUID groupId, UUID id, ScenarioRequest request) {
        GroupMember me = access.requireMember(groupId, userId);
        PlanScenario scenario = findScenario(groupId, id);
        scenario.set(request.name().trim(), scenarioItems(groupId, request.itemIds()));
        return scenarioResponse(scenario, usersOf(List.of(scenario.getUserId())), me);
    }

    @Transactional
    public void deleteScenario(UUID userId, UUID groupId, UUID id) {
        GroupMember me = access.requireMember(groupId, userId);
        PlanScenario scenario = findScenario(groupId, id);
        if (!me.isAdmin() && !userId.equals(scenario.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Só quem criou o cenário ou um administrador pode excluí-lo.");
        }
        scenarios.delete(scenario);
    }

    /** Deixa ativos exatamente os itens do cenário. */
    @Transactional
    public void applyScenario(UUID userId, UUID groupId, UUID id) {
        access.requireMember(groupId, userId);
        Set<UUID> chosen = findScenario(groupId, id).getItemIds();
        items.findByGroupIdOrderByPositionAscCreatedAtAsc(groupId).forEach(i -> i.setActive(chosen.contains(i.getId())));
    }

    /** Quem sai leva os itens que cadastrou (regra dos módulos em grupo). */
    @EventListener
    public void onMemberRemoved(GroupMemberRemovedEvent event) {
        List<PlanItem> own = items.findByGroupIdAndUserId(event.groupId(), event.userId());
        own.forEach(i -> images.deleteAfterCommit(i.getImageKey()));
        items.deleteAll(own);
    }

    /** O banco apaga as linhas em cascata; aqui só as fotos. */
    @EventListener
    public void onGroupDeleted(GroupDeletedEvent event) {
        items.imageKeys(event.groupId()).forEach(images::deleteAfterCommit);
    }

    PlanItem find(UUID groupId, UUID id) {
        return items.findByIdAndGroupId(id, groupId).orElseThrow(() -> new NotFoundException("Item não encontrado."));
    }

    PlanScenario findScenario(UUID groupId, UUID id) {
        return scenarios.findByIdAndGroupId(id, groupId).orElseThrow(() -> new NotFoundException("Cenário não encontrado."));
    }

    static Settings settingsOf(PlanSettings s) {
        return new Settings(s.getReserve(), s.getAverageMonths(), s.getManualBalance());
    }

    List<PlanItemResponse> responses(List<PlanItem> list, GroupMember me) {
        Map<UUID, User> byId = usersOf(list.stream().map(PlanItem::getUserId).toList());
        return list.stream().map(i -> new PlanItemResponse(i.getId(), i.getName(), i.getKind(), i.getAmount(),
                i.getTargetMonth(), i.getInstallments(), i.getPosition(), i.isActive(), i.getNotes(), i.getUrl(),
                i.getLinkTitle(), i.getLinkSite(), i.getLinkPrice(), i.getReferenceSite(), i.getReferenceTitle(),
                i.getReferenceUrl(), i.getReferencePrice(), i.imageUrl(), i.getAcquiredOn(),
                i.getAcquiredAmount(), UserSummary.from(byId.get(i.getUserId())), canEdit(me, i))).toList();
    }

    private void apply(PlanItem item, PlanItemRequest request) {
        YearMonth limit = YearMonth.now().plusYears(MAX_YEARS_AHEAD);
        if (request.targetMonth().isAfter(limit)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O prazo pode ser de até " + MAX_YEARS_AHEAD + " anos.");
        }
        String url = request.url() == null || request.url().isBlank() ? null : LinkPreviewService.parse(request.url()).toString();
        item.set(request.name().trim(), request.kind(), request.amount(), request.targetMonth(),
                request.installmentsOrOne(), blankToNull(request.notes()));
        item.setLink(url, url == null ? null : blankToNull(request.linkTitle()),
                url == null ? null : blankToNull(request.linkSite()), url == null ? null : request.linkPrice());
        String referenceUrl = blankToNull(request.referenceUrl()) == null ? null
                : LinkPreviewService.parse(request.referenceUrl()).toString();
        item.setReference(blankToNull(request.referenceSite()), blankToNull(request.referenceTitle()), referenceUrl,
                request.referencePrice());
        if (request.imageData() != null && !request.imageData().isBlank()) {
            String previous = item.getImageKey();
            item.setImageKey(images.save(decodeImage(request.imageData())));
            images.deleteAfterCommit(previous);
        } else if (request.removeImageOrFalse()) {
            images.deleteAfterCommit(item.getImageKey());
            item.setImageKey(null);
        }
    }

    /** Aceita data URL ({@code data:image/...;base64,...}) ou base64 puro. */
    private static byte[] decodeImage(String data) {
        String value = data.trim();
        if (value.startsWith("data:")) {
            int comma = value.indexOf(',');
            if (comma < 0 || !value.substring(0, comma).endsWith(";base64")) throw invalidImage();
            value = value.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw invalidImage();
        }
    }

    private static BusinessException invalidImage() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "Imagem inválida.");
    }

    /** Itens do cenário (nulo = ativos agora); todos precisam ser do grupo. */
    private Set<UUID> scenarioItems(UUID groupId, Collection<UUID> ids) {
        List<PlanItem> all = items.findByGroupIdOrderByPositionAscCreatedAtAsc(groupId);
        if (ids == null) return all.stream().filter(PlanItem::isActive).map(PlanItem::getId).collect(Collectors.toSet());
        Set<UUID> known = all.stream().map(PlanItem::getId).collect(Collectors.toSet());
        Set<UUID> chosen = new HashSet<>(ids);
        if (!known.containsAll(chosen)) throw new NotFoundException("Item não encontrado.");
        return chosen;
    }

    private ScenarioResponse scenarioResponse(PlanScenario s, Map<UUID, User> byId, GroupMember me) {
        User creator = s.getUserId() == null ? null : byId.get(s.getUserId());
        return new ScenarioResponse(s.getId(), s.getName(), List.copyOf(s.getItemIds()),
                creator == null ? null : UserSummary.from(creator), me.isAdmin() || me.getUserId().equals(s.getUserId()));
    }

    private Map<UUID, User> usersOf(Collection<UUID> ids) {
        Set<UUID> distinct = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return users.findAllById(distinct).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static boolean canEdit(GroupMember me, PlanItem item) {
        return me.isAdmin() || me.getUserId().equals(item.getUserId());
    }

    private static void requireCanEdit(GroupMember me, PlanItem item) {
        if (!canEdit(me, item)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Só quem cadastrou o item ou um administrador pode alterá-lo.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
