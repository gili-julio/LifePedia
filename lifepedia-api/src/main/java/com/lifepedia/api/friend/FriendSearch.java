package com.lifepedia.api.friend;

import com.lifepedia.api.search.SearchHit;
import com.lifepedia.api.search.SearchKind;
import com.lifepedia.api.search.SearchProvider;
import com.lifepedia.api.search.SearchQuery;
import com.lifepedia.api.search.SearchSql;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** Amigos (amizade aceita) por nome e @username na busca geral; nunca o e-mail. */
@Component
public class FriendSearch implements SearchProvider {

    private final JdbcClient jdbc;

    public FriendSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SearchKind> kinds() {
        return Set.of(SearchKind.FRIEND);
    }

    @Override
    public List<SearchHit> search(SearchQuery query, SearchKind kind) {
        return new SearchSql(query).where("(f.requester_id = :userId or f.addressee_id = :userId)")
                .where("f.status = 'ACCEPTED'").text("u.name", List.of("u.name", "u.username"), List.of())
                .list(jdbc, "u.id, u.name, u.username", "friendships f join users u on u.id = "
                        + "case when f.requester_id = :userId then f.addressee_id else f.requester_id end", "f.accepted_at",
                        (rs, n) -> {
                            String username = rs.getString("username");
                            return SearchHit.of(SearchKind.FRIEND, SearchSql.uuid(rs, "id"), rs.getString("name"),
                                            "/amigos/" + SearchSql.uuid(rs, "id"), rs.getDouble("score"))
                                    .subtitle(username == null ? "Amigo" : "@" + username).build();
                        });
    }
}
