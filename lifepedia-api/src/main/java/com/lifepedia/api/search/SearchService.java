package com.lifepedia.api.search;

import com.lifepedia.api.group.GroupAccess;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.search.SearchDtos.SearchGroup;
import com.lifepedia.api.search.SearchDtos.SearchRequest;
import com.lifepedia.api.search.SearchDtos.SearchResponse;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.user.AppModule;
import com.lifepedia.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Busca geral: junta o que cada módulo acha, só nos módulos que a pessoa não escondeu. Precisa de pelo menos 2
 * letras ou de um filtro. Na busca de um grupo, só membros (404) e só o que é do grupo.
 */
@Service
public class SearchService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final int MIN_TEXT = 2;

    private final Map<SearchKind, SearchProvider> providers = new EnumMap<>(SearchKind.class);
    private final UserRepository users;
    private final GroupMemberRepository members;
    private final GroupAccess access;

    public SearchService(List<SearchProvider> providers, UserRepository users, GroupMemberRepository members,
                         GroupAccess access) {
        providers.forEach(p -> p.kinds().forEach(k -> this.providers.put(k, p)));
        this.users = users;
        this.members = members;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(UUID userId, SearchRequest request) {
        SearchQuery query = query(userId, request);
        if (query == null) return SearchResponse.EMPTY;
        Set<AppModule> hidden = new HashSet<>(users.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado.")).hiddenModules());
        List<SearchGroup> groups = new ArrayList<>();
        SearchTotals totals = null;
        for (SearchKind kind : SearchKind.values()) {
            SearchProvider provider = providers.get(kind);
            if (provider == null || !wanted(kind, request, hidden) || !query.accepts(kind)) continue;
            List<SearchHit> hits = provider.search(query, kind);
            if (!hits.isEmpty()) {
                groups.add(new SearchGroup(kind, kind.module(), hits.subList(0, Math.min(hits.size(), query.limit())),
                        hits.size() > query.limit()));
            }
            if (kind == SearchKind.TRANSACTION) totals = provider.totals(query, kind);
        }
        groups.sort(Comparator.comparingDouble((SearchGroup g) -> g.items().getFirst().score()).reversed());
        return new SearchResponse(groups, totals);
    }

    /** Nulo quando não há o que buscar. */
    private SearchQuery query(UUID userId, SearchRequest request) {
        int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();
        if (limit < 1 || limit > MAX_LIMIT) throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite inválido.");
        UUID groupId = request.groupId();
        List<UUID> groupIds;
        if (groupId != null) {
            access.requireMember(groupId, userId);
            groupIds = List.of(groupId);
        } else {
            groupIds = members.findByUserId(userId).stream().map(GroupMember::getGroupId).toList();
        }
        SearchQuery query = SearchQuery.of(userId, request.q(), groupId, groupIds, request.from(), request.to(),
                request.min(), request.max(), request.type(), request.categoryId(), limit);
        return query.text().length() >= MIN_TEXT || query.filtered() ? query : null;
    }

    private static boolean wanted(SearchKind kind, SearchRequest request, Set<AppModule> hidden) {
        return !hidden.contains(kind.module())
                && (request.modules() == null || request.modules().isEmpty() || request.modules().contains(kind.module()))
                && (request.kinds() == null || request.kinds().isEmpty() || request.kinds().contains(kind));
    }
}
