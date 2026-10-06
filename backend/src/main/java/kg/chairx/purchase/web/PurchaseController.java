package kg.chairx.purchase.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import kg.chairx.operations.ReceivePurchase;
import kg.chairx.purchase.api.*;
import kg.chairx.purchase.application.PurchaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {
    private final PurchaseService service;
    private final ReceivePurchase receivePurchase;
    public PurchaseController(PurchaseService service,ReceivePurchase receivePurchase) {
        this.service=service;
        this.receivePurchase=receivePurchase;
    }
    @PostMapping
    public ResponseEntity<PurchaseResponse> create(@Valid @RequestBody CreatePurchaseRequest request) {
        var result=service.create(request);
        return ResponseEntity.created(URI.create("/api/purchases/"+result.id())).body(result);
    }
    @GetMapping("/{id}")
    public PurchaseResponse get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping
    public PurchasePageResponse list(@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.list(page,size); }
    @PutMapping("/{id}")
    public PurchaseResponse update(@PathVariable UUID id,@Valid @RequestBody UpdatePurchaseRequest request) {
        return service.update(id,request);
    }
    @PostMapping("/{id}/confirm")
    public PurchaseResponse confirm(@PathVariable UUID id) { return service.confirm(id); }
    @PostMapping("/{id}/cancel")
    public PurchaseResponse cancel(@PathVariable UUID id) { return service.cancel(id); }
    @PutMapping("/{id}/cargo")
    public PurchaseResponse cargo(@PathVariable UUID id,@Valid @RequestBody SetCargoCostRequest request) {
        return service.setCargo(id,request);
    }
    @PostMapping("/{id}/receipts")
    public ResponseEntity<PurchaseReceiptResponse> receive(@PathVariable UUID id,
            @Valid @RequestBody CreatePurchaseReceiptRequest request) {
        var result=receivePurchase.receive(id,request);
        return ResponseEntity.created(URI.create("/api/purchases/"+id+"/receipts/"+result.id())).body(result);
    }
    @GetMapping("/{id}/receipts/{receiptId}")
    public PurchaseReceiptResponse receipt(@PathVariable UUID id,@PathVariable UUID receiptId) {
        return service.getReceipt(id,receiptId);
    }
    @GetMapping("/{id}/receipts")
    public PurchaseReceiptPageResponse receipts(@PathVariable UUID id,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.receipts(id,page,size); }
}
