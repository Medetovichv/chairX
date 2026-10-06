package kg.chairx.supplier.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import kg.chairx.supplier.api.*;
import kg.chairx.supplier.application.SupplierService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierService service;

    public SupplierController(SupplierService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody CreateSupplierRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/suppliers/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public SupplierResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping
    public SupplierPageResponse list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(page, size);
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSupplierRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public SupplierResponse activate(@PathVariable UUID id) { return service.activate(id); }

    @PostMapping("/{id}/deactivate")
    public SupplierResponse deactivate(@PathVariable UUID id) { return service.deactivate(id); }
}
