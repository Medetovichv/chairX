package kg.chairx.sale.web;

import jakarta.validation.Valid;
import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.api.SaleResponse;
import kg.chairx.sale.api.SalePageResponse;
import kg.chairx.sale.domain.SaleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.LocalDate;
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

    @GetMapping
    public SalePageResponse list(@RequestParam(defaultValue="0") @Min(0) int page,
                                 @RequestParam(defaultValue="20") @Min(1) @Max(100) int size,
                                 @RequestParam(required=false) SaleStatus status,
                                 @RequestParam(required=false) LocalDate from,
                                 @RequestParam(required=false) LocalDate to,
                                 @RequestParam(required=false) String number) {
        return service.list(page,size,status,from,to,number);
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

    @PostMapping("/drafts")
    public ResponseEntity<SaleResponse> createDraft(
            @Valid @RequestBody kg.chairx.sale.api.CreateDraftSaleRequest request) {
        SaleResponse response = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/sales/" + response.id()))
                .body(response);
    }

    @PutMapping("/{id}/draft")
    public SaleResponse updateDraft(@PathVariable UUID id,
            @Valid @RequestBody kg.chairx.sale.api.UpdateDraftSaleRequest request) {
        return service.updateDraft(id, request);
    }

    @PostMapping("/{id}/confirm")
    public SaleResponse confirmDraft(@PathVariable UUID id) {
        return service.confirmDraft(id);
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