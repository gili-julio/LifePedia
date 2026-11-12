package com.lifepedia.api.integration;

import com.lifepedia.api.achievement.Achievement;
import com.lifepedia.api.achievement.AchievementProgress;
import com.lifepedia.api.integration.IntegrationDtos.DuolingoStats;
import com.lifepedia.api.integration.duolingo.DuolingoClient;
import com.lifepedia.api.integration.duolingo.DuolingoClient.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ofensiva e XP do Duolingo; guarda a maior ofensiva já vista (a conquista nunca baixa). */
@Component
class DuolingoSyncer implements AccountSyncer {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final DuolingoClient duolingo;
    private final IntegrationAccountRepository accounts;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate transaction;

    DuolingoSyncer(DuolingoClient duolingo, IntegrationAccountRepository accounts, ApplicationEventPublisher publisher,
                   PlatformTransactionManager transactions) {
        this.duolingo = duolingo;
        this.accounts = accounts;
        this.publisher = publisher;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public boolean supports(IntegrationProvider provider) {
        return provider == IntegrationProvider.DUOLINGO;
    }

    @Override
    public int sync(Connection connection) throws IntegrationException {
        Profile profile = duolingo.profile(connection.login());
        transaction.executeWithoutResult(status -> accounts.findById(connection.accountId()).ifPresent(account -> {
            DuolingoStats before = stats(account.getStats(), null);
            int best = Math.max(profile.streak(), before == null ? 0 : before.bestStreak());
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("streak", profile.streak());
            stats.put("totalXp", profile.totalXp());
            stats.put("bestStreak", best);
            stats.put("lastLesson", profile.lastLesson() == null ? null : profile.lastLesson().toString());
            account.setStats(JSON.writeValueAsString(stats));
            publisher.publishEvent(new AchievementProgress(connection.userId(),
                    EnumSet.of(Achievement.DUOLINGO_STREAK, Achievement.DUOLINGO_XP)));
        }));
        return 1;
    }

    /** Lê o resumo gravado; {@code today} nulo = não calcula se a lição de hoje foi feita. */
    static DuolingoStats stats(String json, LocalDate today) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode node = JSON.readTree(json);
            LocalDate last = null;
            String raw = node.path("lastLesson").asString("");
            if (!raw.isEmpty()) last = LocalDate.parse(raw);
            return new DuolingoStats(node.path("streak").asInt(0), node.path("totalXp").asLong(0), node.path("bestStreak").asInt(0),
                    last, today != null && today.equals(last));
        } catch (JacksonException | DateTimeParseException e) {
            return null;
        }
    }
}
