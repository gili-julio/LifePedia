package com.lifepedia.api.vehicle;

import com.lifepedia.api.user.UserDtos.UserSummary;
import com.lifepedia.api.vehicle.VehicleDtos.DocumentResponse;
import com.lifepedia.api.vehicle.VehicleDtos.ItemResponse;
import com.lifepedia.api.vehicle.VehicleDtos.RevisionResponse;
import com.lifepedia.api.vehicle.VehicleDtos.Upcoming;
import com.lifepedia.api.vehicle.VehicleDtos.UpcomingKind;
import com.lifepedia.api.vehicle.VehicleDtos.VehicleCard;
import com.lifepedia.api.vehicle.VehicleMath.Due;
import com.lifepedia.api.vehicle.VehicleMath.Reading;
import com.lifepedia.api.vehicle.VehicleMath.Stage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Monta as respostas com os vencimentos calculados (puro: tudo chega carregado). */
final class VehicleViews {

    /** Ritmo de uso e km estimado de hoje de um veículo. */
    record Pace(Double kmPerDay, int estimatedKm) {
    }

    /** Tudo o que o cálculo de um veículo precisa. */
    record Loaded(Vehicle vehicle, List<VehicleReading> readings, List<VehicleItem> items, List<VehicleRevision> revisions,
                  List<VehicleDocument> documents) {
    }

    private static final Comparator<Upcoming> URGENT = Comparator
            .comparing((Upcoming u) -> u.due().stage(), Comparator.reverseOrder())
            .thenComparing(u -> u.due().expectedOn(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(u -> u.due().kmLeft(), Comparator.nullsLast(Comparator.naturalOrder()));

    private VehicleViews() {
    }

    static Pace pace(Vehicle vehicle, List<VehicleReading> readings, LocalDate today) {
        Double perDay = VehicleMath.kmPerDay(readings.stream().map(r -> new Reading(r.getReadOn(), r.getKm())).toList());
        return new Pace(perDay, VehicleMath.estimatedKm(vehicle.getOdometer(), vehicle.getOdometerOn(), perDay, today));
    }

    static Due due(VehicleItem item, Pace pace, LocalDate today) {
        return VehicleMath.item(item.getIntervalKm(), item.getIntervalMonths(), item.getLastKm(), item.getLastOn(),
                pace.estimatedKm(), pace.kmPerDay(), today);
    }

    static Due due(VehicleRevision revision, Pace pace, LocalDate today) {
        return revision.isDone() ? null
                : VehicleMath.revision(revision.getDueKm(), revision.getDueOn(), pace.estimatedKm(), pace.kmPerDay(), today);
    }

    static Due due(VehicleDocument document, LocalDate today) {
        return VehicleMath.document(document.getDueOn(), today);
    }

    static ItemResponse item(VehicleItem i, Pace pace, LocalDate today) {
        return new ItemResponse(i.getId(), i.getName(), i.getIntervalKm(), i.getIntervalMonths(), i.getLastKm(), i.getLastOn(),
                i.isNotify(), due(i, pace, today));
    }

    static RevisionResponse revision(VehicleRevision r, Pace pace, LocalDate today) {
        return new RevisionResponse(r.getId(), r.getName(), r.getDueKm(), r.getDueOn(), r.getDoneOn(), r.getDoneKm(), r.getShop(),
                r.getNotes(), r.getExpenseId(), due(r, pace, today));
    }

    static DocumentResponse document(VehicleDocument d, LocalDate today) {
        return new DocumentResponse(d.getId(), d.getKind(), d.getName(), d.getDueOn(), d.getAmount(), d.getNotes(), due(d, today));
    }

    /** Vencimentos (item sem registro e revisão feita não entram), do mais urgente ao mais distante. */
    static List<Upcoming> upcoming(Loaded data, Pace pace, LocalDate today) {
        List<Upcoming> list = new ArrayList<>();
        for (VehicleItem i : data.items()) {
            Due due = due(i, pace, today);
            if (due != null) list.add(new Upcoming(UpcomingKind.ITEM, i.getId(), i.getName(), due));
        }
        for (VehicleRevision r : data.revisions()) {
            Due due = due(r, pace, today);
            if (due != null) list.add(new Upcoming(UpcomingKind.REVISION, r.getId(), r.getName(), due));
        }
        for (VehicleDocument d : data.documents()) {
            list.add(new Upcoming(UpcomingKind.DOCUMENT, d.getId(), d.getName(), due(d, today)));
        }
        list.sort(URGENT);
        return list;
    }

    static VehicleCard card(Loaded data, Pace pace, List<Upcoming> upcoming, UserSummary owner, boolean mine, VehicleRole role,
                            String sharedVia) {
        Vehicle v = data.vehicle();
        int alerts = (int) upcoming.stream().filter(u -> u.due().stage() != Stage.OK).count();
        return new VehicleCard(v.getId(), v.getType(), v.getName(), v.getBrand(), v.getModel(), v.getModelYear(), v.getPlate(),
                v.getColor(), v.getFuel(), v.imageUrl(), v.getOdometer(), v.getOdometerOn(), pace.estimatedKm(), pace.kmPerDay(),
                upcoming.isEmpty() ? null : upcoming.getFirst(), alerts, v.getFipeValue(), owner, mine, role, sharedVia,
                v.getArchivedAt());
    }
}
