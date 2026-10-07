package kg.chairx.refund.web;

import jakarta.validation.Valid;
import kg.chairx.refund.api.CreateRefundRequest;
import kg.chairx.refund.api.RefundResponse;
import kg.chairx.refund.application.RefundService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundService service;

    public RefundController(RefundService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RefundResponse> create(
            @Valid @RequestBody CreateRefundRequest request
    ) {
        RefundResponse response =
                service.create(request);

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/refunds/" + response.id()
                        )
                )
                .body(response);
    }

    @GetMapping("/{id}")
    public RefundResponse get(
            @PathVariable UUID id
    ) {
        return service.get(id);
    }

    @GetMapping("/sale/{saleId}")
    public List<RefundResponse> getBySale(
            @PathVariable UUID saleId
    ) {
        return service.getBySale(saleId);
    }
}