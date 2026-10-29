package com.lifepedia.api.attendance;

import com.lifepedia.api.attendance.AttendanceDtos.AgendaItem;
import com.lifepedia.api.attendance.AttendanceDtos.CommitmentData;
import com.lifepedia.api.attendance.AttendanceDtos.CommitmentRequest;
import com.lifepedia.api.attendance.AttendanceDtos.CommitmentResponse;
import com.lifepedia.api.attendance.AttendanceDtos.Day;
import com.lifepedia.api.attendance.AttendanceDtos.DayStatus;
import com.lifepedia.api.attendance.AttendanceDtos.ImportRequest;
import com.lifepedia.api.attendance.AttendanceDtos.MarkData;
import com.lifepedia.api.attendance.AttendanceDtos.MarkRequest;
import com.lifepedia.api.attendance.AttendanceDtos.Stats;
import com.lifepedia.api.attendance.CommitmentMark.Mark;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.task.TaskLinks;
import com.lifepedia.api.task.TaskLinks.LinkedTask;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Frequência: os dias de cada compromisso saem da regra (dias da semana no período) e só a marcação de cada dia é
 * guardada. Sem marcação, a tarefa ligada concluída no dia conta presença; senão o dia que já passou fica pendente.
 */
@Service
public class AttendanceService {

    static final int MAX_COMMITMENTS = 50;
    static final int MAX_RANGE_DAYS = 62;
    private static final int MAX_YEARS = 5;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CommitmentRepository commitments;
    private final CommitmentMarkRepository marks;
    private final TaskLinks tasks;
    private final Clock clock;

    public AttendanceService(CommitmentRepository commitments, CommitmentMarkRepository marks, TaskLinks tasks,
                             Clock clock) {
        this.commitments = commitments;
        this.marks = marks;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CommitmentResponse> list(UUID userId) {
        List<Commitment> all = commitments.findByUserIdOrderByNameAsc(userId);
        Context context = context(userId, all, LocalDate.now(clock));
        return all.stream().map(c -> response(c, context)).toList();
    }

    @Transactional
    public CommitmentResponse create(UUID userId, CommitmentRequest request) {
        if (commitments.countByUserId(userId) >= MAX_COMMITMENTS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_COMMITMENTS + " compromissos.");
        }
        Commitment commitment = new Commitment(userId);
        apply(userId, commitment, request);
        return single(commitments.save(commitment));
    }

    @Transactional
    public CommitmentResponse update(UUID userId, UUID id, CommitmentRequest request) {
        Commitment commitment = require(userId, id);
        apply(userId, commitment, request);
        return single(commitments.save(commitment));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        commitments.delete(require(userId, id));
    }

    /** Dias que já chegaram (até hoje ou até o fim do período), do mais novo para o mais antigo. */
    @Transactional(readOnly = true)
    public List<Day> days(UUID userId, UUID id) {
        Commitment commitment = require(userId, id);
        LocalDate today = LocalDate.now(clock);
        Context context = context(userId, List.of(commitment), today);
        List<Day> days = new ArrayList<>();
        for (LocalDate date = last(commitment, today); !date.isBefore(commitment.getStartDate()); date = date.minusDays(1)) {
            if (commitment.occursOn(date)) days.add(context.day(commitment, date));
        }
        return days;
    }

    /** Presença e falta só até hoje; "sem aula" vale também para os próximos dias. */
    @Transactional
    public Day mark(UUID userId, UUID id, LocalDate date, MarkRequest request) {
        Commitment commitment = require(userId, id);
        LocalDate today = LocalDate.now(clock);
        Mark status = request.status();
        checkMark(commitment, date, status, today);
        Optional<CommitmentMark> existing = marks.findByCommitmentIdAndDate(id, date);
        if (status == null) {
            existing.ifPresent(marks::delete);
        } else {
            CommitmentMark mark = existing.orElseGet(() -> new CommitmentMark(id, date));
            mark.setStatus(status);
            marks.save(mark);
        }
        marks.flush();
        return context(userId, List.of(commitment), today).day(commitment, date);
    }

    /**
     * Importação (ex.: frequência do SIGAA): cria o compromisso, ou usa o {@code targetId} já existente sem mudar os dados
     * dele, e grava as marcações (a do arquivo vale sobre a que já havia). Tudo ou nada.
     */
    @Transactional
    public CommitmentResponse importDays(UUID userId, ImportRequest request) {
        Commitment commitment;
        if (request.targetId() != null) {
            commitment = require(userId, request.targetId());
        } else if (request.commitment() != null) {
            if (commitments.countByUserId(userId) >= MAX_COMMITMENTS) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_COMMITMENTS + " compromissos.");
            }
            commitment = new Commitment(userId);
            apply(userId, commitment, request.commitment());
            commitment = commitments.save(commitment);
        } else {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Informe o compromisso.");
        }
        LocalDate today = LocalDate.now(clock);
        UUID id = commitment.getId();
        Map<LocalDate, CommitmentMark> existing = new HashMap<>();
        marks.findByCommitmentIdIn(List.of(id)).forEach(m -> existing.put(m.getDate(), m));
        for (MarkData data : request.marks()) {
            checkMark(commitment, data.date(), data.status(), today);
            CommitmentMark mark = existing.computeIfAbsent(data.date(), date -> new CommitmentMark(id, date));
            mark.setStatus(data.status());
        }
        marks.saveAll(existing.values());
        marks.flush();
        return single(commitment);
    }

    @Transactional(readOnly = true)
    public List<AgendaItem> agenda(UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Período inválido (até " + MAX_RANGE_DAYS + " dias).");
        }
        List<Commitment> all = commitments.findByUserIdOrderByNameAsc(userId);
        LocalDate today = LocalDate.now(clock);
        Context context = context(userId, all, to.isAfter(today) ? to : today);
        List<AgendaItem> items = new ArrayList<>();
        for (Commitment c : all) {
            Stats stats = context.stats(c);
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (!c.occursOn(date)) continue;
                Day day = context.day(c, date);
                items.add(new AgendaItem(c.getId(), c.getName(), c.getColor(), date, c.getStartTime(),
                        c.getDurationMinutes(), day.status(), day.fromTask(), context.linkedId(c), stats.absences(),
                        c.getMaxAbsences()));
            }
        }
        items.sort(Comparator.comparing(AgendaItem::date).thenComparing(AgendaItem::time));
        return items;
    }

    /** Tarefas pessoais em aberto que dá para ligar. */
    @Transactional(readOnly = true)
    public List<LinkedTask> linkable(UUID userId) {
        return tasks.open(userId);
    }

    /** Para o arquivo da conta: compromissos com as marcações, em ordem de data. */
    @Transactional(readOnly = true)
    public List<CommitmentData> export(UUID userId) {
        List<Commitment> all = commitments.findByUserIdOrderByNameAsc(userId);
        Context context = context(userId, all, LocalDate.now(clock));
        Map<UUID, List<MarkData>> byCommitment = new HashMap<>();
        if (!all.isEmpty()) {
            marks.findByCommitmentIdIn(all.stream().map(Commitment::getId).toList()).stream()
                    .sorted(Comparator.comparing(CommitmentMark::getDate))
                    .forEach(m -> byCommitment.computeIfAbsent(m.getCommitmentId(), k -> new ArrayList<>())
                            .add(new MarkData(m.getDate(), m.getStatus())));
        }
        return all.stream().map(c -> {
            LinkedTask task = context.linked(c);
            return new CommitmentData(c.getName(), c.getColor(), c.getWeekdays(), c.getStartTime(), c.getDurationMinutes(),
                    c.getStartDate(), c.getEndDate(), c.getMaxAbsences(), task == null ? null : task.title(),
                    byCommitment.getOrDefault(c.getId(), List.of()));
        }).toList();
    }

    private void apply(UUID userId, Commitment commitment, CommitmentRequest request) {
        LocalDate today = LocalDate.now(clock);
        if (request.startDate().isBefore(today.minusYears(MAX_YEARS))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Início no máximo " + MAX_YEARS + " anos atrás.");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O fim precisa ser depois do início.");
        }
        if (request.endDate() != null && request.endDate().isAfter(request.startDate().plusYears(MAX_YEARS))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Período de no máximo " + MAX_YEARS + " anos.");
        }
        if (request.taskId() != null && tasks.personal(userId, request.taskId()).isEmpty()) {
            throw new NotFoundException("Tarefa não encontrada.");
        }
        commitment.update(request.name().trim(), request.color(), request.weekdays(), request.time(),
                request.durationMinutes(), request.startDate(), request.endDate(), request.maxAbsences(),
                request.taskId());
    }

    /** Dia precisa ser do compromisso; presença e falta só até hoje. */
    private static void checkMark(Commitment commitment, LocalDate date, Mark status, LocalDate today) {
        if (!commitment.occursOn(date)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Esse compromisso não acontece em " + DAY.format(date) + ".");
        }
        if (status != null && status != Mark.CANCELED && date.isAfter(today)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Presença e falta só a partir do dia do compromisso.");
        }
    }

    private Commitment require(UUID userId, UUID id) {
        return commitments.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Compromisso não encontrado."));
    }

    private CommitmentResponse single(Commitment commitment) {
        return response(commitment, context(commitment.getUserId(), List.of(commitment), LocalDate.now(clock)));
    }

    private CommitmentResponse response(Commitment c, Context context) {
        return new CommitmentResponse(c.getId(), c.getName(), c.getColor(), c.getWeekdays(), c.getStartTime(),
                c.getDurationMinutes(), c.getStartDate(), c.getEndDate(), c.getMaxAbsences(), context.linked(c),
                context.stats(c));
    }

    /** Marcações e conclusões das tarefas ligadas, do início mais antigo até {@code until}. */
    private Context context(UUID userId, List<Commitment> list, LocalDate until) {
        List<UUID> ids = list.stream().map(Commitment::getId).toList();
        Map<UUID, Map<LocalDate, Mark>> byCommitment = new HashMap<>();
        if (!ids.isEmpty()) {
            for (CommitmentMark m : marks.findByCommitmentIdIn(ids)) {
                byCommitment.computeIfAbsent(m.getCommitmentId(), k -> new HashMap<>()).put(m.getDate(), m.getStatus());
            }
        }
        List<UUID> taskIds = list.stream().map(Commitment::getTaskId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, LinkedTask> linked = taskIds.isEmpty() ? Map.of() : tasks.personal(userId, taskIds);
        LocalDate first = list.stream().map(Commitment::getStartDate).min(Comparator.naturalOrder()).orElse(until);
        Map<UUID, Set<LocalDate>> done = linked.isEmpty() || first.isAfter(until) ? Map.of()
                : tasks.doneDates(userId, linked.keySet(), first, until);
        return new Context(byCommitment, linked, done, LocalDate.now(clock), LocalTime.now(clock));
    }

    private static LocalDate last(Commitment commitment, LocalDate today) {
        return commitment.getEndDate() != null && commitment.getEndDate().isBefore(today) ? commitment.getEndDate() : today;
    }

    private record Context(Map<UUID, Map<LocalDate, Mark>> marks, Map<UUID, LinkedTask> linked,
                           Map<UUID, Set<LocalDate>> done, LocalDate today, LocalTime now) {

        LinkedTask linked(Commitment c) {
            return c.getTaskId() == null ? null : linked.get(c.getTaskId());
        }

        UUID linkedId(Commitment c) {
            LinkedTask task = linked(c);
            return task == null ? null : task.id();
        }

        Day day(Commitment c, LocalDate date) {
            Mark mark = marks.getOrDefault(c.getId(), Map.of()).get(date);
            if (mark != null) return new Day(date, DayStatus.valueOf(mark.name()), false);
            UUID taskId = linkedId(c);
            if (taskId != null && done.getOrDefault(taskId, Set.of()).contains(date)) {
                return new Day(date, DayStatus.PRESENT, true);
            }
            return new Day(date, held(c, date) ? DayStatus.PENDING : DayStatus.UPCOMING, false);
        }

        /** Contagem dos dias que já chegaram (até hoje, ou até o fim do período). */
        Stats stats(Commitment c) {
            int presences = 0;
            int absences = 0;
            int pending = 0;
            int canceled = 0;
            for (LocalDate date = c.getStartDate(); !date.isAfter(last(c, today)); date = date.plusDays(1)) {
                if (!c.occursOn(date)) continue;
                switch (day(c, date).status()) {
                    case PRESENT -> presences++;
                    case ABSENT -> absences++;
                    case PENDING -> pending++;
                    case CANCELED -> canceled++;
                    case UPCOMING -> { }
                }
            }
            return new Stats(presences, absences, pending, canceled, c.getMaxAbsences() - absences, upcoming(c));
        }

        /** Dias de hoje até o fim que ainda vão acontecer (sem os já marcados, como "sem aula"). */
        private Integer upcoming(Commitment c) {
            if (c.getEndDate() == null) return null;
            int count = 0;
            LocalDate from = c.getStartDate().isAfter(today) ? c.getStartDate() : today;
            for (LocalDate date = from; !date.isAfter(c.getEndDate()); date = date.plusDays(1)) {
                if (c.occursOn(date) && day(c, date).status() == DayStatus.UPCOMING) count++;
            }
            return count;
        }

        /** Já terminou (dia passado, ou hoje depois do fim do horário). */
        private boolean held(Commitment c, LocalDate date) {
            if (!date.equals(today)) return date.isBefore(today);
            return c.getStartTime().toSecondOfDay() + c.getDurationMinutes() * 60L <= now.toSecondOfDay();
        }
    }
}
