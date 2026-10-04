package kg.chairx.warehouse.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import kg.chairx.warehouse.api.*;
import kg.chairx.warehouse.application.WarehouseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {
    private final WarehouseService service;

    public WarehouseController(WarehouseService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<WarehouseResponse> create(@Valid @RequestBody CreateWarehouseRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/warehouses/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public WarehouseResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping
    public WarehousePageResponse list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(page, size);
    }

    @PutMapping("/{id}")
    public WarehouseResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateWarehouseRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public WarehouseResponse activate(@PathVariable UUID id) { return service.activate(id); }

    @PostMapping("/{id}/deactivate")
    public WarehouseResponse deactivate(@PathVariable UUID id) { return service.deactivate(id); }
}
