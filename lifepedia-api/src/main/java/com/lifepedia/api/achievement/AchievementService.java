package com.lifepedia.api.achievement;

import com.lifepedia.api.achievement.AchievementDtos.AchievementResponse;
import com.lifepedia.api.finance.transaction.TransactionRepository;
import com.lifepedia.api.friend.Friendship;
import com.lifepedia.api.friend.FriendshipRepository;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.task.TaskAgendaService;
import com.lifepedia.api.task.TaskDtos.HabitSummary;
import com.lifepedia.api.task.TaskOccurrence.Status;
import com.lifepedia.api.task.TaskOccurrenceRepository;
import com.lifepedia.api.task.TaskScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Conquistas: cada nível alcançado é gravado com a data e não se perde mais; só o próximo nível depende das contagens
 * atuais (no nível máximo nada é contado). Confere depois de cada ação ({@link AchievementProgress}) e ao ler.
 */
@Service
public class AchievementService {

    private final UserAchievementRepository unlocked;
    private final TaskOccurrenceRepository occurrences;
    private final TaskAgendaService agenda;
    private final FriendshipRepository friendships;
    private final GroupMemberRepository members;
    private final TransactionRepository transactions;

    public AchievementService(UserAchievementRepository unlocked, TaskOccurrenceRepository occurrences,
                              TaskAgendaService agenda, FriendshipRepository friendships, GroupMemberRepository members,
                              TransactionRepository transactions) {
        this.unlocked = unlocked;
        this.occurrences = occurrences;
        this.agenda = agenda;
        this.friendships = friendships;
        this.members = members;
        this.transactions = transactions;
    }

    /** @param owner quem pede é o dono (inclui as conquistas que amigos não veem) */
    @Transactional
    public List<AchievementResponse> of(UUID userId, boolean owner) {
        Map<Achievement, UserAchievement> reached = reached(userId);
        return Arrays.stream(Achievement.values())
                .filter(a -> owner || !a.isOwnerOnly())
                .map(a -> progress(userId, a, reached.get(a)))
                .toList();
    }

    /** Depois do commit da ação, numa transação própria (falha aqui não desfaz a ação). */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProgress(AchievementProgress event) {
        Map<Achievement, UserAchievement> reached = reached(event.userId());
        event.codes().forEach(a -> progress(event.userId(), a, reached.get(a)));
    }

    /** Grava os níveis novos que o valor atual alcança e monta a resposta. */
    private AchievementResponse progress(UUID userId, Achievement code, UserAchievement last) {
        int level = last == null ? 0 : last.getLevel();
        OffsetDateTime unlockedAt = last == null ? null : last.getUnlockedAt();
        if (level >= code.maxLevel()) return AchievementResponse.of(code, code.threshold(level), level, unlockedAt);
        long value = value(code, userId);
        int now = code.levelOf(value);
        if (now > level) {
            unlockedAt = OffsetDateTime.now();
            for (int l = level + 1; l <= now; l++) unlocked.unlock(userId, code.name(), l, unlockedAt);
            level = now;
        }
        return AchievementResponse.of(code, value, level, unlockedAt);
    }

    /** Nível mais alto já gravado de cada conquista. */
    private Map<Achievement, UserAchievement> reached(UUID userId) {
        return unlocked.findByUserId(userId).stream().collect(Collectors.toMap(UserAchievement::getCode,
                Function.identity(), BinaryOperator.maxBy(Comparator.comparingInt(UserAchievement::getLevel))));
    }

    private long value(Achievement achievement, UUID userId) {
        return switch (achievement) {
            case TASKS_DONE -> occurrences.countByCompletedByAndStatus(userId, Status.DONE);
            case GROUP_TASKS_DONE -> occurrences.countInGroupsByCompletedBy(userId, Status.DONE);
            case HABIT_STREAK -> agenda.habits(TaskScope.personal(userId)).stream()
                    .mapToInt(HabitSummary::best).max().orElse(0);
            case FRIENDS -> friendships.countInvolving(userId, Friendship.Status.ACCEPTED);
            case GROUPS -> members.countByUserId(userId);
            case TRANSACTIONS -> transactions.countByUserId(userId);
        };
    }
}
