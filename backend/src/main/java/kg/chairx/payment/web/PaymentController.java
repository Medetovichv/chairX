package kg.chairx.payment.web;

import jakarta.validation.Valid;
import kg.chairx.payment.api.CancelPaymentRequest;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.api.PaymentResponse;
import kg.chairx.payment.application.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        PaymentResponse response =
                service.create(request);

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/payments/" + response.id()
                        )
                )
                .body(response);
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