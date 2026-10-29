package com.lifepedia.api.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lifepedia.api.attendance.CommitmentMark.Mark;
import com.lifepedia.api.task.TaskLinks.LinkedTask;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    /**
     * @param weekdays 1 = segunda ... 7 = domingo
     * @param endDate  fim do período (nulo = sem fim)
     * @param taskId   tarefa pessoal ligada: concluída no dia conta presença
     */
    public record CommitmentRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120, message = "Máximo de 120 caracteres.") String name,
            @NotNull(message = "Escolha a cor.")
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida.") String color,
            @NotEmpty(message = "Escolha os dias.") @Size(max = 7, message = "Dias inválidos.")
            List<@NotNull @Min(value = 1, message = "Dia inválido.") @Max(value = 7, message = "Dia inválido.") Integer> weekdays,
            @NotNull(message = "Informe o horário.") @JsonFormat(pattern = "HH:mm") LocalTime time,
            @NotNull(message = "Informe a duração.") @Min(value = 5, message = "Duração mínima de 5 minutos.")
            @Max(value = 720, message = "Duração máxima de 12 horas.") Integer durationMinutes,
            @NotNull(message = "Informe o início.") LocalDate startDate,
            LocalDate endDate,
            @NotNull(message = "Informe o limite de faltas.") @Min(value = 0, message = "Limite inválido.")
            @Max(value = 999, message = "Limite inválido.") Integer maxAbsences,
            UUID taskId) {
    }

    /** Situação de um dia: marcado, pendente (já aconteceu e não foi marcado) ou ainda por vir. */
    public enum DayStatus { PRESENT, ABSENT, CANCELED, PENDING, UPCOMING }

    /** @param remaining faltas que ainda cabem no limite (negativo = passou) */
    public record Stats(int presences, int absences, int pending, int canceled, int remaining) {
    }

    public record CommitmentResponse(UUID id, String name, String color, List<Integer> weekdays,
                                     @JsonFormat(pattern = "HH:mm") LocalTime time, int durationMinutes,
                                     LocalDate startDate, LocalDate endDate, int maxAbsences, LinkedTask task,
                                     Stats stats) {
    }

    /** @param fromTask presença vinda da tarefa ligada concluída (sem marcação) */
    public record Day(LocalDate date, DayStatus status, boolean fromTask) {
    }

    /** @param status nulo = tira a marcação (volta a pendente, ou presente pela tarefa) */
    public record MarkRequest(Mark status) {
    }

    /** Compromisso no arquivo da conta (só registro, não importa); {@code task} = título da tarefa ligada. */
    public record CommitmentData(String name, String color, List<Integer> weekdays,
                                 @JsonFormat(pattern = "HH:mm") LocalTime time, int durationMinutes, LocalDate startDate,
                                 LocalDate endDate, int maxAbsences, String task, List<MarkData> marks) {
    }

    public record MarkData(@NotNull(message = "Informe o dia.") LocalDate date,
                           @NotNull(message = "Informe a marcação.") Mark status) {
    }

    /**
     * Importação (ex.: SIGAA): {@code targetId} = compromisso já existente (só as marcações mudam); senão cria com
     * {@code commitment}.
     */
    public record ImportRequest(@Valid CommitmentRequest commitment, UUID targetId,
                                @NotNull(message = "Informe as marcações.") @Size(max = 500, message = "Dias demais.")
                                List<@NotNull @Valid MarkData> marks) {
    }

    /** Um dia do compromisso na agenda, com as faltas do compromisso inteiro. */
    public record AgendaItem(UUID commitmentId, String name, String color, LocalDate date,
                             @JsonFormat(pattern = "HH:mm") LocalTime time, int durationMinutes, DayStatus status,
                             boolean fromTask, UUID taskId, int absences, int maxAbsences) {
    }
}
