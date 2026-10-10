package kg.chairx.purchase.web;

import kg.chairx.purchase.api.*;
import kg.chairx.purchase.application.PurchasePaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchases/{purchaseId}/payments")
public class PurchasePaymentController {
    private final PurchasePaymentService service;
    public PurchasePaymentController(PurchasePaymentService service) { this.service=service; }

    @PostMapping
    public ResponseEntity<PurchasePaymentResponse> create(
            @PathVariable UUID purchaseId,
            @Valid @RequestBody CreatePurchasePaymentRequest request) {
        var response=service.create(purchaseId,request);
        return ResponseEntity.created(URI.create(
                "/api/purchases/"+purchaseId+"/payments/"+response.id())).body(response);
    }

    @GetMapping
    public PurchasePaymentSummaryResponse list(@PathVariable UUID purchaseId) {
        return service.list(purchaseId);
    }
}
