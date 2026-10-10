package kg.chairx.inventory.web;

import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;

import kg.chairx.inventory.api.CreateInventoryTransferRequest;
import kg.chairx.inventory.api.InventoryTransferResponse;
import kg.chairx.inventory.application.InventoryTransferQueryService;
import kg.chairx.inventory.application.InventoryTransferService;
import kg.chairx.inventory.api.InventoryTransferDetailsResponse;
import kg.chairx.inventory.api.InventoryTransferPageResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/transfers")
public class InventoryTransferController {

    private final InventoryTransferService service;
    private final InventoryTransferQueryService queryService;

    public InventoryTransferController(
            InventoryTransferService service,
            InventoryTransferQueryService queryService
    ) {
        this.service = service;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<InventoryTransferResponse> create(
            @Valid @RequestBody CreateInventoryTransferRequest request,
            Principal principal
    ) {
        var result = service.transfer(
                request.transferId(),
                request.sourceWarehouseId(),
                request.destinationWarehouseId(),
                request.variantId(),
                request.quantity(),
                principal.getName()
        );

        var response = new InventoryTransferResponse(
                result.transferId(),
                result.outMovementId(),
                result.inMovementId(),
                result.quantity()
        );

        return ResponseEntity
                .created(URI.create(
                        "/api/inventory/transfers/" + response.transferId()
                ))
                .body(response);
    }

    @GetMapping("/{id}")
    public InventoryTransferDetailsResponse get(@PathVariable UUID id) {
        return queryService.get(id);
    }

    @GetMapping
    public InventoryTransferPageResponse list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID variantId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return queryService.list(
                warehouseId, variantId, from, to, page, size
        );
    }
}
