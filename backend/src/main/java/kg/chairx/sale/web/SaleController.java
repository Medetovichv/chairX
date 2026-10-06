package kg.chairx.sale.web;

import jakarta.validation.Valid;
import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.api.SaleResponse;
import kg.chairx.sale.application.SaleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService service;

    public SaleController(SaleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SaleResponse> create(
            @Valid @RequestBody CreateSaleRequest request
    ) {
        SaleResponse response = service.create(request);

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/sales/" + response.id()
                        )
                )
                .body(response);
    }

    @GetMapping("/{id}")
    public SaleResponse get(
            @PathVariable UUID id
    ) {
        return service.get(id);
    }

    @PostMapping("/{id}/fulfill")
    public SaleResponse fulfill(
            @PathVariable UUID id
    ) {
        return service.fulfill(id);
    }

    @PostMapping("/{id}/cancel")
    public SaleResponse cancel(
            @PathVariable UUID id
    ) {
        return service.cancel(id);
    }
}