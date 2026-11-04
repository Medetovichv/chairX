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
    private final kg.chairx.sale.application.SaleHistoryService history;
    private final kg.chairx.sale.application.PaidPickupService pickup;

    public SaleController(SaleService service, kg.chairx.sale.application.SaleHistoryService history,
                          kg.chairx.sale.application.PaidPickupService pickup) {
        this.service = service;
        this.history = history;
        this.pickup = pickup;
    }

    @GetMapping
    public SalePageResponse list(@RequestParam(defaultValue="0") @Min(0) int page,
                                 @RequestParam(defaultValue="20") @Min(1) @Max(100) int size,
                                 @RequestParam(required=false) SaleStatus status,
                                 @RequestParam(required=false) LocalDate from,
                                 @RequestParam(required=false) LocalDate to,
                                 @RequestParam(required=false) String number,
                                 @RequestParam(defaultValue="false") boolean operational,
                                 @RequestParam(defaultValue="false") boolean activeOnly) {
        return service.list(page,size,status,from,to,number,operational,activeOnly);
    }

    @GetMapping("/customer/{customerId}")
    public SalePageResponse customerSales(@PathVariable UUID customerId,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.listForCustomer(customerId, page, size);
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

    @GetMapping("/{id}/history")
    public kg.chairx.sale.api.SaleHistoryPage history(@PathVariable UUID id,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return history.list(id, page, size);
    }

    @GetMapping("/{id}")
    public SaleResponse get(
            @PathVariable UUID id
    ) {
        return service.get(id);
    }

    @PostMapping("/{id}/pay-and-hand-over")
    public SaleResponse payAndHandOver(@PathVariable UUID id,
            @Valid @RequestBody kg.chairx.payment.api.CreatePaymentRequest request) {
        return pickup.payAndHandOver(id,request);
    }

    @PostMapping("/{id}/fulfill")
    public SaleResponse fulfill(
            @PathVariable UUID id
    ) {
        return service.fulfill(id);
    }

    @PostMapping("/{id}/cancel-with-reason")
    public SaleResponse cancelWithReason(@PathVariable UUID id,
            @Valid @RequestBody kg.chairx.sale.api.CancelSaleRequest request) {
        return service.cancelWithReason(id, request);
    }

    @GetMapping("/{id}/cancellation-details")
    public ResponseEntity<kg.chairx.sale.api.SaleCancellationDetails> cancellationDetails(
            @PathVariable UUID id) {
        return service.cancellationDetails(id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Legacy URL remains for older clients, but cancellation always requires
    // the same validated reason and audit metadata as the canonical endpoint.
    @PostMapping("/{id}/cancel")
    public SaleResponse cancel(
            @PathVariable UUID id,
            @Valid @RequestBody kg.chairx.sale.api.CancelSaleRequest request
    ) {
        return service.cancelWithReason(id, request);
    }
}