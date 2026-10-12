package kg.chairx.sale.web;

import jakarta.validation.Valid;
import kg.chairx.sale.api.CreateSaleWithDeliveryRequest;
import kg.chairx.sale.api.SaleResponse;
import kg.chairx.sale.application.SaleWithDeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/sales/with-delivery")
public class SaleWithDeliveryController {
    private final SaleWithDeliveryService service;
    public SaleWithDeliveryController(SaleWithDeliveryService service) { this.service=service; }

    @PostMapping
    public ResponseEntity<SaleResponse> create(@Valid @RequestBody CreateSaleWithDeliveryRequest request) {
        var sale=service.create(request);
        return ResponseEntity.created(URI.create("/api/sales/"+sale.id())).body(sale);
    }
}
