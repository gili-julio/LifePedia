package com.lifepedia.api.vehicle;

import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.vehicle.VehicleDtos.DocumentRequest;
import com.lifepedia.api.vehicle.VehicleDtos.DoneRequest;
import com.lifepedia.api.vehicle.VehicleDtos.ItemRequest;
import com.lifepedia.api.vehicle.VehicleDtos.PlanRequest;
import com.lifepedia.api.vehicle.VehicleDtos.RenewRequest;
import com.lifepedia.api.vehicle.VehicleDtos.RevisionRequest;
import com.lifepedia.api.vehicle.VehicleDtos.VehicleDetail;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Itens de manutenção, plano de revisões e documentos: cadastro (quem edita) e "feito"/"pago" (quem lança). */
@Service
public class VehicleCareService {

    static final int MAX_ITEMS = 40;
    static final int MAX_REVISIONS = 30;
    static final int MAX_DOCUMENTS = 20;
    private static final Locale BR = Locale.of("pt", "BR");

    private final VehicleItemRepository items;
    private final VehicleRevisionRepository revisions;
    private final VehicleDocumentRepository documents;
    private final VehicleReadingRepository readings;
    private final VehicleServiceLogRepository logs;
    private final VehicleAccess access;
    private final VehicleLogService logbook;
    private final VehicleService vehicles;
    private final Clock clock;

    public VehicleCareService(VehicleItemRepository items, VehicleRevisionRepository revisions, VehicleDocumentRepository documents,
                              VehicleReadingRepository readings, VehicleServiceLogRepository logs, VehicleAccess access,
                              VehicleLogService log, VehicleService vehicles, Clock clock) {
        this.items = items;
        this.revisions = revisions;
        this.documents = documents;
        this.readings = readings;
        this.logs = logs;
        this.access = access;
        this.logbook = log;
        this.vehicles = vehicles;
        this.clock = clock;
    }

    @Transactional
    public VehicleDetail addItem(UUID userId, UUID vehicleId, ItemRequest request) {
        access.require(vehicleId, userId, VehicleRole.EDIT);
        long count = items.countByVehicleId(vehicleId);
        if (count >= MAX_ITEMS) throw bad("Limite de " + MAX_ITEMS + " itens de manutenção.");
        VehicleItem item = new VehicleItem(vehicleId, (int) count);
        apply(item, request);
        items.save(item);
        return vehicles.get(userId, vehicleId);
    }

    @Transactional
    public VehicleDetail updateItem(UUID userId, UUID vehicleId, UUID itemId, ItemRequest request) {
        access.require(vehicleId, userId, VehicleRole.EDIT);
        apply(item(vehicleId, itemId), request);
        return vehicles.get(userId, vehicleId);
    }

    @Transactional
    public VehicleDetail deleteItem(UUID userId, UUID vehicleId, UUID itemId) {
        access.require(vehicleId, userId, VehicleRole.EDIT);
        items.delete(item(vehicleId, itemId));
        return vehicles.get(userId, vehicleId);
    }

    /** Feito agora (ou numa data): vira a "última vez", entra no histórico e, com valor, lança o gasto. */
    @Transactional
    public VehicleDetail itemDone(UUID userId, UUID vehicleId, UUID itemId, DoneRequest request) {
        Vehicle vehicle = access.require(vehicleId, userId, VehicleRole.LOG).vehicle();
        VehicleItem item = item(vehicleId, itemId);
        VehicleExpense expense = request.amount() == null ? null : logbook.addExpense(vehicle, userId, ExpenseKind.SERVICE, request.date(),
                request.amount(), item.getName(), request.km(), notes(request), request.finance());
        int km = request.km() != null ? request.km() : kmOn(vehicle, request.date());
        markDone(item, km, request.date());
        logs.save(new VehicleServiceLog(vehicleId, item.getId(), null, userId, expense == null ? null : expense.getId(), item.getName(),
                request.date(), km, logNotes(request)));
        return vehicles.get(userId, vehicleId);
    }

    @Transactional
    public VehicleDetail addRevision(UUID userId, UUID vehicleId, RevisionRequest request) {
        access.require(vehicleId, userId, VehicleRole.EDIT);
        requireRevisionRoom(vehicleId, 1);
        VehicleRevision revision = new VehicleRevision(vehicleId);
        apply(revision, request);
        revisions.save(revision);
        return vehicles.get(userId, vehicleId);
    }

    @Transactional
    public VehicleDetail updateRevision(UUID userId, UUID vehicleId, UUID revisionId, RevisionRequest request) {
        access.require(vehicleId, userId, VehicleRole.EDIT);
        apply(revision(vehicleId, revisionId), request);
        return vehicles.get(userId, vehicleId);
    }

    @Transactional
    public VehicleDetail deleteRevision(UUID userId, UUID vehicleId, UUID revisionId) {
        access.require(vehicleId, userId, VehicleRole.EDIT);
        revisions.delete(revision(vehicleId, revisionId));
        return vehicles.get(userId, vehicleId);
    }

    /**
     * Gera as próximas revisões (as que já passaram do km atual ficam de fora). Meses contados de {@code since} (ex.: compra)
     * ou, sem ela, de hoje a cada revisão criada.
     */
    @Transactional
    public VehicleDetail plan(UUID userId, UUID vehicleId, PlanRequest request) {
        Vehicle vehicle = access.require(vehicleId, userId, VehicleRole.EDIT).vehicle();
        if (request.everyKm() == null && request.everyMonths() == null) throw bad("Informe o intervalo em km ou em meses.");
        requireRevisionRoom(vehicleId, request.count());
        LocalDate today = LocalDate.now(clock);
        int created = 0;
        for (int n = 1; n <= 200 && created < request.count(); n++) {
            Integer dueKm = request.everyKm() == null ? null : n * request.everyKm();
            long months = request.everyMonths() == null ? 0 : request.everyMonths();
            LocalDate dueOn = months == 0 ? null
                    : request.since() != null ? request.since().plusMonths(n * months) : today.plusMonths((created + 1L) * months);
            boolean past = dueKm != null ? dueKm <= vehicle.getOdometer() : dueOn.isBefore(today);
            if (past) continue;
            VehicleRevision revision = new VehicleRevision(vehicleId);
            revision.set(dueKm != null ? "Revisão de " + String.format(BR, "%,d", dueKm) + " km" : "Revisão " + n, dueKm, dueOn, null);
            revisions.save(revision);
            created++;
        }
        return vehicles.get(userId, vehicleId);
    }

    /** Revisão feita: data, km, oficina, gasto (opcional) e os itens trocados nela. */
    @Transactional
    public VehicleDetail revisionDone(UUID userId, UUID vehicleId, UUID revisionId, DoneRequest request) {
        Vehicle vehicle = access.require(vehicleId, userId, VehicleRole.LOG).vehicle();
        VehicleRevision revision = revision(vehicleId, revisionId);
        VehicleExpense expense = request.amount() == null ? null : logbook.addExpense(vehicle, userId, ExpenseKind.SERVICE, request.date(),
                request.amount(), revision.getName(), request.km(), notes(request), request.finance());
        int km = request.km() != null ? request.km() : kmOn(vehicle, request.date());
        revision.done(request.date(), request.km() != null ? request.km() : km, clean(request.shop()),
                expense == null ? null : expense.getId());
        UUID expenseId = expense == null ? null : expense.getId();
        logs.save(new VehicleServiceLog(vehicleId, null, revisionId, userId, expenseId, revision.getName(), request.date(), km,
                logNotes(request)));
        List<UUID> ids = request.itemIds() == null ? List.of() : request.itemIds();
        for (VehicleItem item : ids.isEmpty() ? List.<VehicleItem>of() : items.findByVehicleIdAndIdIn(vehicleId, ids)) {
            markDone(item, km, request.date());
            logs.save(new VehicleServiceLog(vehicleId, item.getId(), revisionId, userId, null, item.getName(), request.date(), km, null));
        }
        return vehicles.get(userId, vehicleId);
    }

    /** Desfaz o "feita" (o gasto lançado continua nos gastos). */
    @Transactional
    public VehicleDetail reopenRevision(UUID userId, UUID vehicleId, UUID revisionId) {
        access.require(vehicleId, userId, VehicleRole.EDIT);
        revision(vehicleId, revisionId).reopen();
        return vehicles.get(userId, vehicleId);
    }

    @Transactional
    public VehicleDetail addDocument(UUID userId, UUID vehicleId, DocumentRequest request) {
        access.require(vehicleId, userId, VehicleRole.EDIT);
        if (documents.countByVehicleId(vehicleId) >= MAX_DOCUMENTS) throw bad("Limite de " + MAX_DOCUMENTS + " documentos.");
        VehicleDocument document = new VehicleDocument(vehicleId);
        apply(document, request);
        documents.save(document);
        return vehicles.get(userId, vehicleId);
    }

    @Transactional
    public VehicleDetail updateDocument(UUID userId, UUID vehicleId, UUID documentId, DocumentRequest request) {
        access.require(vehicleId, userId, VehicleRole.EDIT);
        apply(document(vehicleId, documentId), request);
        return vehicles.get(userId, vehicleId);
    }

    @Transactional
    public VehicleDetail deleteDocument(UUID userId, UUID vehicleId, UUID documentId) {
        access.require(vehicleId, userId, VehicleRole.EDIT);
        documents.delete(document(vehicleId, documentId));
        return vehicles.get(userId, vehicleId);
    }

    /** Pago/renovado: próximo vencimento e, com valor, o gasto do tipo do documento. */
    @Transactional
    public VehicleDetail renew(UUID userId, UUID vehicleId, UUID documentId, RenewRequest request) {
        Vehicle vehicle = access.require(vehicleId, userId, VehicleRole.LOG).vehicle();
        VehicleDocument document = document(vehicleId, documentId);
        if (!request.nextDue().isAfter(document.getDueOn())) throw bad("O próximo vencimento precisa ser depois do atual.");
        if (request.amount() != null) {
            LocalDate paidOn = request.paidOn() != null ? request.paidOn() : LocalDate.now(clock);
            logbook.addExpense(vehicle, userId, document.getKind().expense(), paidOn, request.amount(), document.getName(), null, null,
                    request.finance());
        }
        document.renew(request.nextDue());
        return vehicles.get(userId, vehicleId);
    }

    private void markDone(VehicleItem item, int km, LocalDate date) {
        if (item.getLastOn() == null || !date.isBefore(item.getLastOn())) item.setLast(km, date);
    }

    /** Km na data: a leitura do dia ou antes; sem leitura, o odômetro atual. */
    private int kmOn(Vehicle vehicle, LocalDate date) {
        Integer before = readings.maxBefore(vehicle.getId(), date.plusDays(1), null);
        return before != null ? before : vehicle.getOdometer();
    }

    private void requireRevisionRoom(UUID vehicleId, int adding) {
        if (revisions.countByVehicleId(vehicleId) + adding > MAX_REVISIONS) throw bad("Limite de " + MAX_REVISIONS + " revisões.");
    }

    private VehicleItem item(UUID vehicleId, UUID id) {
        return items.findByIdAndVehicleId(id, vehicleId).orElseThrow(() -> new NotFoundException("Item não encontrado."));
    }

    private VehicleRevision revision(UUID vehicleId, UUID id) {
        return revisions.findByIdAndVehicleId(id, vehicleId).orElseThrow(() -> new NotFoundException("Revisão não encontrada."));
    }

    private VehicleDocument document(UUID vehicleId, UUID id) {
        return documents.findByIdAndVehicleId(id, vehicleId).orElseThrow(() -> new NotFoundException("Documento não encontrado."));
    }

    private static void apply(VehicleItem item, ItemRequest r) {
        if (r.intervalKm() == null && r.intervalMonths() == null) throw bad("Informe o intervalo em km ou em meses.");
        item.set(r.name().trim(), r.intervalKm(), r.intervalMonths(), !Boolean.FALSE.equals(r.alert()));
        if (r.lastKm() != null || r.lastOn() != null) item.setLast(r.lastKm(), r.lastOn());
    }

    private static void apply(VehicleRevision revision, RevisionRequest r) {
        if (r.dueKm() == null && r.dueOn() == null) throw bad("Informe o km ou a data da revisão.");
        revision.set(r.name().trim(), r.dueKm(), r.dueOn(), clean(r.notes()));
    }

    private static void apply(VehicleDocument document, DocumentRequest r) {
        document.set(r.kind(), r.name().trim(), r.dueOn(), r.amount(), clean(r.notes()));
    }

    private static String notes(DoneRequest r) {
        String shop = clean(r.shop());
        String notes = clean(r.notes());
        if (shop == null) return notes;
        return notes == null ? "Oficina: " + shop : "Oficina: " + shop + "\n" + notes;
    }

    private static String logNotes(DoneRequest r) {
        String text = notes(r);
        return text == null || text.length() <= 500 ? text : text.substring(0, 500);
    }

    private static String clean(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static BusinessException bad(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}
