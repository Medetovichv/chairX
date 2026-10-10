package kg.chairx.delivery.web;

import jakarta.validation.Valid;
import kg.chairx.delivery.api.CreateDeliveryRequest;
import kg.chairx.delivery.api.DeliveryResponse;
import kg.chairx.delivery.api.DeliveryPageResponse;
import kg.chairx.delivery.domain.DeliveryStatus;
import java.time.LocalDate;
import kg.chairx.delivery.api.FailDeliveryRequest;
import kg.chairx.delivery.api.ReturnDeliveryToWarehouseRequest;
import kg.chairx.delivery.application.DeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @GetMapping
    public DeliveryPageResponse list(@RequestParam(required=false) DeliveryStatus status,
            @RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to,
            @RequestParam(required=false) LocalDate plannedFrom,
            @RequestParam(required=false) LocalDate plannedTo,
            @RequestParam(required=false) String cityRegion,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return service.list(status,from,to,plannedFrom,plannedTo,cityRegion,page,size);
    }

    @PostMapping
    public ResponseEntity<DeliveryResponse> create(
            @Valid @RequestBody CreateDeliveryRequest request
    ) {
        DeliveryResponse response = service.create(request);

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/deliveries/" + response.id()
                        )
                )
                .body(response);
    }

    @GetMapping("/{id}")
    public DeliveryResponse get(
            @PathVariable UUID id
    ) {
        return service.get(id);
    }

    @PutMapping("/{id}/planned-date")
    public DeliveryResponse changePlannedDate(
            @PathVariable UUID id,
            @Valid @RequestBody kg.chairx.delivery.api.UpdatePlannedDeliveryDateRequest request
    ) {
        return service.changePlannedDate(id, request.plannedDeliveryDate());
    }

    @PostMapping("/{id}/dispatch")
    public DeliveryResponse dispatch(
            @PathVariable UUID id
    ) {
        return service.dispatch(id);
    }

    @PostMapping("/{id}/deliver")
    public DeliveryResponse deliver(
            @PathVariable UUID id
    ) {
        return service.markDelivered(id);
    }

    @PostMapping("/{id}/fail")
    public DeliveryResponse fail(
            @PathVariable UUID id,
            @Valid @RequestBody FailDeliveryRequest request
    ) {
        return service.markFailed(
                id,
                request
        );
    }

    @PostMapping("/{id}/cancel")
    public DeliveryResponse cancel(
            @PathVariable UUID id
    ) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/return-to-warehouse")
    public DeliveryResponse returnToWarehouse(
            @PathVariable UUID id,
            @Valid @RequestBody ReturnDeliveryToWarehouseRequest request
    ) {
        return service.returnToWarehouse(
                id,
                request
        );
    }
}