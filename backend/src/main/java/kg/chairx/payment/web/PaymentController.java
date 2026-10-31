package kg.chairx.payment.web;

import jakarta.validation.Valid;
import kg.chairx.payment.api.CancelPaymentRequest;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.api.PaymentResponse;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.application.PickupCheckoutService;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.domain.FulfillmentType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;
    private final PickupCheckoutService pickupCheckout;
    private final SaleService sales;

    public PaymentController(PaymentService service, PickupCheckoutService pickupCheckout,
                             SaleService sales) {
        this.service = service;
        this.pickupCheckout = pickupCheckout;
        this.sales = sales;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        // Every public pickup payment route must use one atomic transaction;
        // direct POST /api/payments must not leave a paid-but-unfulfilled pickup.
        PaymentResponse response = sales.get(request.saleId()).fulfillmentType() == FulfillmentType.SELF_PICKUP
                ? pickupCheckout.checkout(request)
                : service.create(request);

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/payments/" + response.id()
                        )
                )
                .body(response);
    }

    @PostMapping("/pickup-checkout")
    public PaymentResponse checkoutPickup(@Valid @RequestBody CreatePaymentRequest request) {
        return pickupCheckout.checkout(request);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(
            @PathVariable UUID id
    ) {
        return service.get(id);
    }

    @GetMapping("/sale/{saleId}")
    public List<PaymentResponse> getBySale(
            @PathVariable UUID saleId
    ) {
        return service.getBySale(saleId);
    }

    @GetMapping("/sale/{saleId}/active")
    public PaymentResponse getActiveBySale(
            @PathVariable UUID saleId
    ) {
        return service.getActiveBySale(saleId);
    }

    @PostMapping("/{id}/cancel")
    public PaymentResponse cancel(
            @PathVariable UUID id,
            @Valid @RequestBody CancelPaymentRequest request
    ) {
        return service.cancel(
                id,
                request
        );
    }
}