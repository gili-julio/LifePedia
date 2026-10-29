package com.lifepedia.api.task;

import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.task.TaskDtos.AgendaSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Horas visíveis e sono da agenda, por pessoa (sem registro: {@link #DEFAULT}). */
@Service
public class AgendaPreferenceService {

    private static final int DEFAULT_SLEEP = 8 * 60;
    static final AgendaSettings DEFAULT = new AgendaSettings(6, 23, List.of(
            DEFAULT_SLEEP, DEFAULT_SLEEP, DEFAULT_SLEEP, DEFAULT_SLEEP, DEFAULT_SLEEP, DEFAULT_SLEEP, DEFAULT_SLEEP));
    private static final int MIN_HOURS = 4;

    private final AgendaPreferenceRepository preferences;

    public AgendaPreferenceService(AgendaPreferenceRepository preferences) {
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public AgendaSettings get(UUID userId) {
        return preferences.findByUserId(userId).map(AgendaPreferenceService::settings).orElse(DEFAULT);
    }

    @Transactional
    public AgendaSettings save(UUID userId, AgendaSettings request) {
        if (request.dayEnd() - request.dayStart() < MIN_HOURS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mostre pelo menos " + MIN_HOURS + " horas.");
        }
        AgendaPreference preference = preferences.findByUserId(userId).orElse(null);
        List<Integer> sleep = request.sleepMinutes() != null ? request.sleepMinutes()
                : preference != null ? settings(preference).sleepMinutes() : DEFAULT.sleepMinutes();
        if (preference == null) {
            preference = new AgendaPreference(userId);
        }
        preference.set(request.dayStart(), request.dayEnd(), sleep.stream().mapToInt(Integer::intValue).toArray());
        return settings(preferences.save(preference));
    }

    private static AgendaSettings settings(AgendaPreference preference) {
        return new AgendaSettings(preference.getDayStart(), preference.getDayEnd(),
                Arrays.stream(preference.getSleepMinutes()).boxed().toList());
    }
}
