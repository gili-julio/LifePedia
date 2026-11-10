package com.lifepedia.api.admin;

import com.lifepedia.api.admin.AdminDtos.Database;
import com.lifepedia.api.admin.AdminDtos.Features;
import com.lifepedia.api.admin.AdminDtos.Job;
import com.lifepedia.api.admin.AdminDtos.SystemStatus;
import com.lifepedia.api.config.AdminProperties;
import com.lifepedia.api.config.AiProperties;
import com.lifepedia.api.config.GoogleProperties;
import com.lifepedia.api.config.PushProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.TaskExecutionOutcome;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Situação do servidor para o painel: versão, memória, banco, recursos ligados e tarefas agendadas. */
@Service
public class AdminStatusService {

    private static final long MB = 1024 * 1024;

    private final JdbcClient jdbc;
    private final ObjectProvider<BuildProperties> build;
    private final ObjectProvider<ScheduledTaskHolder> scheduled;
    private final AiProperties ai;
    private final PushProperties push;
    private final GoogleProperties google;
    private final AdminProperties admin;
    private final Clock clock;

    public AdminStatusService(JdbcClient jdbc, ObjectProvider<BuildProperties> build, ObjectProvider<ScheduledTaskHolder> scheduled,
                              AiProperties ai, PushProperties push, GoogleProperties google, AdminProperties admin, Clock clock) {
        this.jdbc = jdbc;
        this.build = build;
        this.scheduled = scheduled;
        this.ai = ai;
        this.push = push;
        this.google = google;
        this.admin = admin;
        this.clock = clock;
    }

    public SystemStatus status() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Runtime memory = Runtime.getRuntime();
        BuildProperties info = build.getIfAvailable();
        return new SystemStatus(info == null ? null : info.getVersion(), info == null ? null : info.getTime(),
                Instant.ofEpochMilli(runtime.getStartTime()), runtime.getUptime() / 1000, Runtime.version().toString(),
                clock.getZone().getId(), (memory.totalMemory() - memory.freeMemory()) / MB, memory.maxMemory() / MB,
                database(), new Features(ai.enabled(), ai.enabled() ? ai.model() : null, push.enabled(), google.enabled(),
                admin.normalizedEmail() != null), jobs());
    }

    private Database database() {
        try {
            long start = System.nanoTime();
            jdbc.sql("select 1").query(Integer.class).single();
            long latency = (System.nanoTime() - start) / 1_000_000;
            String version = jdbc.sql("show server_version").query(String.class).single();
            long size = jdbc.sql("select pg_database_size(current_database())").query(Long.class).single();
            String schema = jdbc.sql("""
                    select version from flyway_schema_history where success and version is not null
                     order by installed_rank desc limit 1""").query(String.class).optional().orElse(null);
            return new Database(true, latency, version, size, schema);
        } catch (DataAccessException e) {
            return new Database(false, 0, null, 0, null);
        }
    }

    private List<Job> jobs() {
        return scheduled.orderedStream().flatMap(holder -> holder.getScheduledTasks().stream())
                .map(AdminStatusService::job).sorted(Comparator.comparing(Job::name)).toList();
    }

    private static Job job(ScheduledTask task) {
        TaskExecutionOutcome outcome = task.getTask().getLastExecutionOutcome();
        return new Job(name(task.getTask().getRunnable().toString()), outcome.executionTime(), outcome.status().name(),
                task.nextExecution());
    }

    /** "com.lifepedia.api.task.TaskReminderJob.remind" → "TaskReminderJob.remind". */
    private static String name(String qualified) {
        int method = qualified.lastIndexOf('.');
        int type = method > 0 ? qualified.lastIndexOf('.', method - 1) : -1;
        return type >= 0 ? qualified.substring(type + 1) : qualified;
    }
}
