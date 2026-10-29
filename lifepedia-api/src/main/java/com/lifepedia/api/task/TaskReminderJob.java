package com.lifepedia.api.task;

import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.task.TaskAgendaService.Pending;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A cada minuto (fuso do app): avisa dos lembretes que venceram. Pessoal: o dono; grupo: os responsáveis que ainda
 * não fizeram a sua parte ou, sem responsável, todos os membros. Se a API ficou parada, recupera no máximo {@link #MAX_CATCH_UP_MINUTES}.
 */
@Component
public class TaskReminderJob {

    static final int MAX_CATCH_UP_MINUTES = 10;
    private static final int MINUTES_PER_DAY = 24 * 60;
    /** Lembrete mais antecipado (30 dias, igual à validação da tarefa). */
    private static final int MAX_REACH_DAYS = 30;

    private final TaskRepository tasks;
    private final TaskAgendaService agenda;
    private final TaskNotifier notifier;
    private final GroupMemberRepository members;
    private final Clock clock;
    private LocalDateTime last;

    public TaskReminderJob(TaskRepository tasks, TaskAgendaService agenda, TaskNotifier notifier,
                           GroupMemberRepository members, Clock clock) {
        this.tasks = tasks;
        this.agenda = agenda;
        this.notifier = notifier;
        this.members = members;
        this.clock = clock;
    }

    @Scheduled(cron = "0 * * * * *", zone = "${lifepedia.time.zone}")
    public synchronized void run() {
        // Arredonda: o agendador às vezes dispara um pouco antes do minuto cheio.
        LocalDateTime now = LocalDateTime.now(clock).plusSeconds(30).truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime from = last == null || last.isBefore(now.minusMinutes(MAX_CATCH_UP_MINUTES)) ? now.minusMinutes(1) : last;
        if (!now.isAfter(from)) return;
        remind(from, now);
        last = now;
    }

    /** Avisa dos lembretes com momento em ({@code from}, {@code to}]; devolve quantos avisos saíram. */
    @Transactional
    public int remind(LocalDateTime from, LocalDateTime to) {
        LocalDate first = from.toLocalDate();
        // Cada tarefa olha só até onde o lembrete dela alcança (1 dia antes: o dia seguinte; 30 dias: um mês à frente).
        Map<Long, List<Task>> byReach = tasks.withReminders(first, to.toLocalDate().plusDays(MAX_REACH_DAYS)).stream()
                .collect(Collectors.groupingBy(TaskReminderJob::reachDays));
        int sent = 0;
        for (Map.Entry<Long, List<Task>> group : byReach.entrySet()) {
            LocalDate lastDay = to.toLocalDate().plusDays(group.getKey());
            for (Pending p : agenda.pending(group.getValue(), first, lastDay)) {
                Integer minutes = p.task().getReminderMinutes();
                if (p.time() == null || minutes == null) continue;
                LocalDateTime at = p.shown().atTime(p.time()).minusMinutes(minutes);
                if (!at.isAfter(from) || at.isAfter(to)) continue;
                sent += notifier.reminder(p.task(), p.shown(), p.time(), p.title(), recipients(p));
            }
        }
        return sent;
    }

    private List<UUID> recipients(Pending p) {
        Task task = p.task();
        if (task.getGroupId() == null) return List.of(task.getUserId());
        if (!p.assignees().isEmpty()) return p.assignees();
        return members.findByGroupIdOrderByCreatedAtAsc(task.getGroupId()).stream().map(GroupMember::getUserId).toList();
    }

    /** Dias à frente que o lembrete alcança (pelo menos 1: o horário pode ser no dia seguinte). */
    private static long reachDays(Task task) {
        return Math.max(1, (task.getReminderMinutes() + MINUTES_PER_DAY - 1) / MINUTES_PER_DAY);
    }
}
