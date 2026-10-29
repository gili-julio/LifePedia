package com.lifepedia.api.task;

import com.lifepedia.api.task.TaskAgendaService.Filter;
import com.lifepedia.api.task.TaskDtos.Sort;
import com.lifepedia.api.task.TaskDtos.TaskItem;
import com.lifepedia.api.task.TaskOccurrence.Status;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Todo dia às 8h (fuso do app): avisa os responsáveis que não fizeram a sua parte (ou, sem responsável, quem criou)
 * das ocorrências de grupo que ficaram de ontem.
 */
@Component
public class TaskOverdueJob {

    private final TaskRepository tasks;
    private final TaskAgendaService agenda;
    private final TaskService service;
    private final TaskNotifier notifier;

    public TaskOverdueJob(TaskRepository tasks, TaskAgendaService agenda, TaskService service, TaskNotifier notifier) {
        this.tasks = tasks;
        this.agenda = agenda;
        this.service = service;
        this.notifier = notifier;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "${lifepedia.time.zone}")
    public void run() {
        notifyOverdue(service.today().minusDays(1));
    }

    /** Avisa das pendentes de {@code day}; devolve quantas. */
    @Transactional
    public int notifyOverdue(LocalDate day) {
        int sent = 0;
        for (UUID groupId : tasks.groupsWithTasksOn(day)) {
            TaskScope scope = new TaskScope(TaskOwner.group(groupId), null, false);
            for (TaskItem item : agenda.agenda(scope, day, day, false, Filter.NONE, Sort.MANUAL).days().getFirst().items()) {
                if (item.status() != Status.PENDING) continue;
                Task task = service.find(scope.owner(), item.taskId());
                List<UUID> to = item.assignees().isEmpty() ? Collections.singletonList(item.createdBy())
                        : item.assignees().stream().filter(p -> !item.doneBy().contains(p)).toList();
                for (UUID person : to) notifier.overdue(task, item.date(), item.title(), person);
                sent++;
            }
        }
        return sent;
    }
}
