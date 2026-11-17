package com.lifepedia.api.vehicle;

import com.lifepedia.api.shared.CurrentUser;
import com.lifepedia.api.shared.image.Images;
import com.lifepedia.api.vehicle.VehicleDtos.DocumentRequest;
import com.lifepedia.api.vehicle.VehicleDtos.DoneRequest;
import com.lifepedia.api.vehicle.VehicleDtos.ExpenseRequest;
import com.lifepedia.api.vehicle.VehicleDtos.ExpenseResponse;
import com.lifepedia.api.vehicle.VehicleDtos.ItemRequest;
import com.lifepedia.api.vehicle.VehicleDtos.LogResponse;
import com.lifepedia.api.vehicle.VehicleDtos.PlanRequest;
import com.lifepedia.api.vehicle.VehicleDtos.ReadingRequest;
import com.lifepedia.api.vehicle.VehicleDtos.ReadingResponse;
import com.lifepedia.api.vehicle.VehicleDtos.RenewRequest;
import com.lifepedia.api.vehicle.VehicleDtos.RevisionRequest;
import com.lifepedia.api.vehicle.VehicleDtos.Stats;
import com.lifepedia.api.vehicle.VehicleDtos.VehicleDetail;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Dentro de um veículo: km, gastos, números, histórico, itens de manutenção, revisões e documentos. */
@RestController
@RequestMapping("/api/vehicles/{vehicleId}")
public class VehicleLogController {

    private final VehicleLogService logs;
    private final VehicleCareService care;

    public VehicleLogController(VehicleLogService logs, VehicleCareService care) {
        this.logs = logs;
        this.care = care;
    }

    @GetMapping("/readings")
    public List<ReadingResponse> readings(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId) {
        return logs.readings(CurrentUser.id(jwt), vehicleId);
    }

    @PostMapping("/readings")
    @ResponseStatus(HttpStatus.CREATED)
    public ReadingResponse addReading(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId,
                                      @Valid @RequestBody ReadingRequest request) {
        return logs.addReading(CurrentUser.id(jwt), vehicleId, request);
    }

    @DeleteMapping("/readings/{readingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReading(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID readingId) {
        logs.deleteReading(CurrentUser.id(jwt), vehicleId, readingId);
    }

    @GetMapping("/expenses")
    public List<ExpenseResponse> expenses(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId,
                                          @RequestParam(defaultValue = "12") int months) {
        return logs.expenses(CurrentUser.id(jwt), vehicleId, months);
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse createExpense(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId,
                                         @Valid @RequestBody ExpenseRequest request) {
        return logs.createExpense(CurrentUser.id(jwt), vehicleId, request);
    }

    @PutMapping("/expenses/{expenseId}")
    public ExpenseResponse updateExpense(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID expenseId,
                                         @Valid @RequestBody ExpenseRequest request) {
        return logs.updateExpense(CurrentUser.id(jwt), vehicleId, expenseId, request);
    }

    @DeleteMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID expenseId) {
        logs.deleteExpense(CurrentUser.id(jwt), vehicleId, expenseId);
    }

    /** Multipart {@code file}: foto do comprovante, JPEG/PNG até 1 MB. */
    @PutMapping(value = "/expenses/{expenseId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExpenseResponse setExpenseImage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID expenseId,
                                           @RequestParam("file") MultipartFile file) {
        return logs.setExpenseImage(CurrentUser.id(jwt), vehicleId, expenseId, Images.uploadBytes(file));
    }

    @DeleteMapping("/expenses/{expenseId}/image")
    public ExpenseResponse removeExpenseImage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId,
                                              @PathVariable UUID expenseId) {
        return logs.removeExpenseImage(CurrentUser.id(jwt), vehicleId, expenseId);
    }

    @GetMapping("/expenses/{expenseId}/image")
    public ResponseEntity<byte[]> expenseImage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId,
                                               @PathVariable UUID expenseId) {
        return logs.expenseImage(CurrentUser.id(jwt), vehicleId, expenseId).response();
    }

    @GetMapping("/stats")
    public Stats stats(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @RequestParam(defaultValue = "12") int months) {
        return logs.stats(CurrentUser.id(jwt), vehicleId, months);
    }

    @GetMapping("/logs")
    public List<LogResponse> logs(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId) {
        return logs.logs(CurrentUser.id(jwt), vehicleId);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleDetail addItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @Valid @RequestBody ItemRequest request) {
        return care.addItem(CurrentUser.id(jwt), vehicleId, request);
    }

    @PutMapping("/items/{itemId}")
    public VehicleDetail updateItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID itemId,
                                    @Valid @RequestBody ItemRequest request) {
        return care.updateItem(CurrentUser.id(jwt), vehicleId, itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public VehicleDetail deleteItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID itemId) {
        return care.deleteItem(CurrentUser.id(jwt), vehicleId, itemId);
    }

    @PostMapping("/items/{itemId}/done")
    public VehicleDetail itemDone(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID itemId,
                                  @Valid @RequestBody DoneRequest request) {
        return care.itemDone(CurrentUser.id(jwt), vehicleId, itemId, request);
    }

    @PostMapping("/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleDetail addRevision(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId,
                                     @Valid @RequestBody RevisionRequest request) {
        return care.addRevision(CurrentUser.id(jwt), vehicleId, request);
    }

    @PostMapping("/revisions/plan")
    public VehicleDetail plan(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @Valid @RequestBody PlanRequest request) {
        return care.plan(CurrentUser.id(jwt), vehicleId, request);
    }

    @PutMapping("/revisions/{revisionId}")
    public VehicleDetail updateRevision(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID revisionId,
                                        @Valid @RequestBody RevisionRequest request) {
        return care.updateRevision(CurrentUser.id(jwt), vehicleId, revisionId, request);
    }

    @DeleteMapping("/revisions/{revisionId}")
    public VehicleDetail deleteRevision(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID revisionId) {
        return care.deleteRevision(CurrentUser.id(jwt), vehicleId, revisionId);
    }

    @PostMapping("/revisions/{revisionId}/done")
    public VehicleDetail revisionDone(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID revisionId,
                                      @Valid @RequestBody DoneRequest request) {
        return care.revisionDone(CurrentUser.id(jwt), vehicleId, revisionId, request);
    }

    @DeleteMapping("/revisions/{revisionId}/done")
    public VehicleDetail reopenRevision(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID revisionId) {
        return care.reopenRevision(CurrentUser.id(jwt), vehicleId, revisionId);
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleDetail addDocument(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId,
                                     @Valid @RequestBody DocumentRequest request) {
        return care.addDocument(CurrentUser.id(jwt), vehicleId, request);
    }

    @PutMapping("/documents/{documentId}")
    public VehicleDetail updateDocument(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID documentId,
                                        @Valid @RequestBody DocumentRequest request) {
        return care.updateDocument(CurrentUser.id(jwt), vehicleId, documentId, request);
    }

    @DeleteMapping("/documents/{documentId}")
    public VehicleDetail deleteDocument(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID documentId) {
        return care.deleteDocument(CurrentUser.id(jwt), vehicleId, documentId);
    }

    @PostMapping("/documents/{documentId}/renew")
    public VehicleDetail renew(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId, @PathVariable UUID documentId,
                               @Valid @RequestBody RenewRequest request) {
        return care.renew(CurrentUser.id(jwt), vehicleId, documentId, request);
    }
}
