package com.lifepedia.api.admin;

import com.lifepedia.api.admin.AdminDtos.AiDay;
import com.lifepedia.api.admin.AdminDtos.DayCount;
import com.lifepedia.api.admin.AdminDtos.ModuleUsage;
import com.lifepedia.api.admin.AdminDtos.Overview;
import com.lifepedia.api.shared.ai.AiUsageLog;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contagens do painel direto no banco (só leitura, várias tabelas de módulos). Nunca devolve conteúdo: só números.
 */
@Component
class AdminStats {

    static final int DAYS = 30;
    static final int WEEKS = 26;

    /** Pessoas e itens por módulo (mais usado primeiro); tarefas e notas de grupo contam para quem criou. */
    private static final String MODULES = """
            select * from (
            select 'FINANCE' as k, count(distinct user_id) as users, count(*) as items
              from (select user_id from transactions union all select user_id from recurring_entries) f
            union all select 'TASKS', count(distinct coalesce(created_by, user_id)), count(*) from tasks
            union all select 'NOTES', count(distinct coalesce(created_by, user_id)), count(*) from notes
            union all select 'ATTENDANCE', count(distinct user_id), count(*) from commitments
            union all select 'FINANCING', (select count(distinct user_id) from financing_members), (select count(*) from financings)
            union all select 'GROUPS', (select count(distinct user_id) from group_members), (select count(*) from user_groups)
            union all select 'CHAT', count(distinct user_id), count(*) from group_messages
            union all select 'FRIENDS', (select count(distinct u) from (select requester_id as u from friendships
                where status = 'ACCEPTED' union select addressee_id from friendships where status = 'ACCEPTED') p),
              (select count(*) from friendships where status = 'ACCEPTED')
            union all select 'AI_IMPORT', count(distinct user_id), count(*) from ai_usage where feature = :feature
            union all select 'PUSH', count(distinct user_id), count(*) from push_subscriptions where session_id is not null
            ) m order by users desc, k
            """;

    private static final String USER_COUNTS = """
            select (select count(*) from transactions where user_id = :id) as transactions,
                   (select count(*) from recurring_entries where user_id = :id) as recurring,
                   (select count(*) from tasks where user_id = :id or created_by = :id) as tasks,
                   (select count(*) from notes where user_id = :id or created_by = :id) as notes,
                   (select count(*) from commitments where user_id = :id) as commitments,
                   (select count(*) from financing_members where user_id = :id) as financings,
                   (select count(*) from group_members where user_id = :id) as groups,
                   (select count(*) from friendships where status = 'ACCEPTED'
                      and (requester_id = :id or addressee_id = :id)) as friends,
                   (select count(*) from group_messages where user_id = :id) as messages,
                   (select count(*) from ai_usage where user_id = :id and created_at >= :since) as ai_imports
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    AdminStats(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    Overview overview() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate today = LocalDate.now(clock);
        LocalDate firstDay = today.minusDays(DAYS - 1L);
        LocalDate firstWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS - 1L);
        String zone = clock.getZone().getId();
        long[] totals = jdbc.sql("""
                        select count(*), count(*) filter (where created_at >= :d7), count(*) filter (where created_at >= :d30),
                               count(*) filter (where last_seen_at >= :d7), count(*) filter (where last_seen_at >= :d30),
                               count(*) filter (where admin), count(*) filter (where blocked_at is not null)
                          from users""")
                .param("d7", now.minusDays(7)).param("d30", now.minusDays(DAYS))
                .query((rs, n) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5),
                        rs.getLong(6), rs.getLong(7)})
                .single();
        Map<LocalDate, Long> byDay = counts("""
                select (created_at at time zone :zone)::date, count(*) from users
                 where created_at >= :since group by 1""", zone, firstDay);
        Map<LocalDate, Long> byWeek = counts("""
                select date_trunc('week', created_at at time zone :zone)::date, count(*) from users
                 where created_at >= :since group by 1""", zone, firstWeek);
        List<DayCount> daily = new ArrayList<>();
        for (LocalDate d = firstDay; !d.isAfter(today); d = d.plusDays(1)) daily.add(new DayCount(d, byDay.getOrDefault(d, 0L)));
        List<DayCount> weekly = new ArrayList<>();
        for (LocalDate w = firstWeek; !w.isAfter(today); w = w.plusWeeks(1)) weekly.add(new DayCount(w, byWeek.getOrDefault(w, 0L)));
        return new Overview(totals[0], totals[1], totals[2], totals[3], totals[4], totals[5], totals[6], daily, weekly);
    }

    long totalUsers() {
        return jdbc.sql("select count(*) from users").query(Long.class).single();
    }

    List<ModuleUsage> modules() {
        return jdbc.sql(MODULES).param("feature", AiUsageLog.IMPORT)
                .query((rs, n) -> new ModuleUsage(rs.getString(1), rs.getLong(2), rs.getLong(3))).list();
    }

    Map<String, Long> userCounts(UUID userId) {
        return jdbc.sql(USER_COUNTS).param("id", userId).param("since", OffsetDateTime.now(clock).minusDays(DAYS))
                .query(AdminStats::columns).single();
    }

    long activeSessions(UUID userId) {
        return jdbc.sql("select count(*) from user_sessions where user_id = :id and expires_at > now()")
                .param("id", userId).query(Long.class).single();
    }

    long pushDevices(UUID userId) {
        return jdbc.sql("select count(*) from push_subscriptions where user_id = :id and session_id is not null")
                .param("id", userId).query(Long.class).single();
    }

    long aiLast24h() {
        return jdbc.sql("select count(*) from ai_usage where feature = :feature and created_at >= :since")
                .param("feature", AiUsageLog.IMPORT).param("since", OffsetDateTime.now(clock).minusDays(1))
                .query(Long.class).single();
    }

    long aiRunning() {
        return jdbc.sql("select count(*) from import_drafts where status = 'RUNNING'").query(Long.class).single();
    }

    /** {@code [pessoas, duração média das leituras ok]} em 30 dias. */
    long[] aiUsersAndDuration() {
        return jdbc.sql("""
                        select count(distinct user_id), coalesce(avg(duration_ms) filter (where outcome = 'SUCCESS'), 0)
                          from ai_usage where feature = :feature and created_at >= :since""")
                .param("feature", AiUsageLog.IMPORT).param("since", OffsetDateTime.now(clock).minusDays(DAYS))
                .query((rs, n) -> new long[] {rs.getLong(1), Math.round(rs.getDouble(2))}).single();
    }

    Map<String, Long> aiOutcomes() {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.sql("""
                        select outcome, count(*) from ai_usage where feature = :feature and created_at >= :since
                         group by outcome order by count(*) desc""")
                .param("feature", AiUsageLog.IMPORT).param("since", OffsetDateTime.now(clock).minusDays(DAYS))
                .query((ResultSet rs) -> {
                    result.put(rs.getString(1), rs.getLong(2));
                });
        return result;
    }

    List<AiDay> aiDaily() {
        LocalDate today = LocalDate.now(clock);
        LocalDate first = today.minusDays(DAYS - 1L);
        Map<LocalDate, long[]> byDay = new HashMap<>();
        jdbc.sql("""
                        select (created_at at time zone :zone)::date, count(*) filter (where outcome = 'SUCCESS'),
                               count(*) filter (where outcome <> 'SUCCESS')
                          from ai_usage where feature = :feature and created_at >= :since group by 1""")
                .param("zone", clock.getZone().getId()).param("feature", AiUsageLog.IMPORT)
                .param("since", first.atStartOfDay(clock.getZone()).toOffsetDateTime())
                .query((ResultSet rs) -> {
                    byDay.put(rs.getObject(1, LocalDate.class), new long[] {rs.getLong(2), rs.getLong(3)});
                });
        List<AiDay> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            long[] c = byDay.getOrDefault(d, new long[2]);
            days.add(new AiDay(d, c[0], c[1]));
        }
        return days;
    }

    private Map<LocalDate, Long> counts(String sql, String zone, LocalDate since) {
        Map<LocalDate, Long> result = new HashMap<>();
        jdbc.sql(sql).param("zone", zone).param("since", since.atStartOfDay(clock.getZone()).toOffsetDateTime())
                .query((ResultSet rs) -> {
                    result.put(rs.getObject(1, LocalDate.class), rs.getLong(2));
                });
        return result;
    }

    private static Map<String, Long> columns(ResultSet rs, int row) throws SQLException {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            result.put(rs.getMetaData().getColumnLabel(i), rs.getLong(i));
        }
        return result;
    }
}
