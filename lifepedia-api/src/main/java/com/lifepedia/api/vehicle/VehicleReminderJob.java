package com.lifepedia.api.vehicle;

import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.vehicle.VehicleDtos.UpcomingKind;
import com.lifepedia.api.vehicle.VehicleMath.Due;
import com.lifepedia.api.vehicle.VehicleMath.Stage;
import com.lifepedia.api.vehicle.VehicleViews.Pace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Todo dia às 9h: avisa (dono e quem lança) quando um item, revisão ou documento chega perto de vencer e quando vence,
 * uma vez em cada etapa. Às 5h atualiza o valor da FIPE dos veículos lidos há mais de 20 dias.
 */
@Component
public class VehicleReminderJob {

    private static final Logger log = LoggerFactory.getLogger(VehicleReminderJob.class);

    private final VehicleRepository vehicles;
    private final VehicleReadingRepository readings;
    private final VehicleItemRepository items;
    private final VehicleRevisionRepository revisions;
    private final VehicleDocumentRepository documents;
    private final VehicleAccess access;
    private final NotificationService notifications;
    private final VehicleService service;
    private final Clock clock;

    public VehicleReminderJob(VehicleRepository vehicles, VehicleReadingRepository readings, VehicleItemRepository items,
                              VehicleRevisionRepository revisions, VehicleDocumentRepository documents, VehicleAccess access,
                              NotificationService notifications, VehicleService service, Clock clock) {
        this.vehicles = vehicles;
        this.readings = readings;
        this.items = items;
        this.revisions = revisions;
        this.documents = documents;
        this.access = access;
        this.notifications = notifications;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "${lifepedia.time.zone}")
    public void run() {
        remind();
    }

    @Scheduled(cron = "0 0 5 * * *", zone = "${lifepedia.time.zone}")
    public void fipe() {
        int done = service.refreshFipeDue();
        if (done > 0) log.info("Valor da FIPE atualizado em {} veículos.", done);
    }

    /** Confere todos os veículos ativos; devolve quantos avisos saíram. */
    @Transactional
    public int remind() {
        LocalDate today = LocalDate.now(clock);
        int sent = 0;
        for (Vehicle vehicle : vehicles.findByArchivedAtIsNull()) {
            UUID id = vehicle.getId();
            List<VehicleItem> itemList = items.findByVehicleIdOrderByPositionAscCreatedAtAsc(id);
            List<VehicleRevision> revisionList = revisions.findByVehicleIdOrderByDueKmAscDueOnAscCreatedAtAsc(id);
            List<VehicleDocument> documentList = documents.findByVehicleIdOrderByDueOnAsc(id);
            if (itemList.isEmpty() && revisionList.isEmpty() && documentList.isEmpty()) continue;
            Pace pace = VehicleViews.pace(vehicle, readings.findByVehicleIdOrderByReadOnAscKmAsc(id), today);
            Set<UUID> people = null;
            for (VehicleItem item : itemList) {
                if (!item.isNotify()) continue;
                Stage stage = next(VehicleViews.due(item, pace, today), item.getWarned());
                if (stage == null) continue;
                item.setWarned(stage);
                people = people == null ? access.people(vehicle, VehicleRole.LOG) : people;
                sent += send(people, vehicle, UpcomingKind.ITEM, item.getName(), VehicleViews.due(item, pace, today));
            }
            for (VehicleRevision revision : revisionList) {
                Stage stage = next(VehicleViews.due(revision, pace, today), revision.getWarned());
                if (stage == null) continue;
                revision.setWarned(stage);
                people = people == null ? access.people(vehicle, VehicleRole.LOG) : people;
                sent += send(people, vehicle, UpcomingKind.REVISION, revision.getName(), VehicleViews.due(revision, pace, today));
            }
            for (VehicleDocument document : documentList) {
                Stage stage = next(VehicleViews.due(document, today), document.getWarned());
                if (stage == null) continue;
                document.setWarned(stage);
                people = people == null ? access.people(vehicle, VehicleRole.LOG) : people;
                sent += send(people, vehicle, UpcomingKind.DOCUMENT, document.getName(), VehicleViews.due(document, today));
            }
        }
        return sent;
    }

    private int send(Set<UUID> people, Vehicle vehicle, UpcomingKind kind, String name, Due due) {
        Map<String, String> data = new HashMap<>();
        data.put("vehicleId", vehicle.getId().toString());
        data.put("vehicleName", vehicle.getName());
        data.put("kind", kind.name());
        data.put("name", name);
        data.put("stage", due.stage().name());
        if (due.expectedOn() != null) data.put("date", due.expectedOn().toString());
        if (due.kmLeft() != null) data.put("kmLeft", String.valueOf(due.kmLeft()));
        notifications.notifyAll(people, NotificationType.VEHICLE_REMINDER, null, data);
        return people.size();
    }

    /** Etapa a avisar agora (pior que a já avisada); nulo se nada muda. */
    private static Stage next(Due due, Stage warned) {
        if (due == null || due.stage() == Stage.OK) return null;
        return warned == null || due.stage().compareTo(warned) > 0 ? due.stage() : null;
    }
}
