package com.lifepedia.api.note;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

/** Toda madrugada apaga de vez as notas que estão na lixeira há mais de 30 dias (com as fotos). */
@Component
public class NoteTrashJob {

    private final NoteService notes;
    private final Clock clock;

    public NoteTrashJob(NoteService notes, Clock clock) {
        this.notes = notes;
        this.clock = clock;
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "${lifepedia.time.zone}")
    public void run() {
        notes.purgeTrash(OffsetDateTime.now(clock));
    }
}
