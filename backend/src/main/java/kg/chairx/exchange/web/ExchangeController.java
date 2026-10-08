package kg.chairx.exchange.web;

import jakarta.validation.Valid;
import kg.chairx.exchange.api.CreateExchangeRequest;
import kg.chairx.exchange.api.CreateExchangeSettlementRequest;
import kg.chairx.exchange.api.ExchangeResponse;
import kg.chairx.exchange.api.ExchangeSettlementResponse;
import kg.chairx.exchange.application.ExchangeService;
import kg.chairx.exchange.application.ExchangeSettlementService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/exchanges")
public class ExchangeController {

    private final ExchangeService service;
    private final ExchangeSettlementService settlementService;

    public ExchangeController(
            ExchangeService service,
            ExchangeSettlementService settlementService
    ) {
        this.service = service;
        this.settlementService = settlementService;
    }

    @PostMapping
    public ResponseEntity<ExchangeResponse> create(
            @Valid @RequestBody CreateExchangeRequest request
    ) {
        ExchangeResponse response = service.create(request);

        return ResponseEntity
                .created(URI.create("/api/exchanges/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ExchangeResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/settlements")
    public ResponseEntity<ExchangeSettlementResponse> settle(
            @PathVariable UUID id,
            @Valid @RequestBody CreateExchangeSettlementRequest request
    ) {
        var settlement = settlementService.settle(
                id,
                request.idempotencyKey(),
                request.direction(),
                request.method(),
                request.amount(),
                request.reference(),
                actor()
        );

        var response = ExchangeSettlementResponse.from(settlement);

        return ResponseEntity
                .created(URI.create(
                        "/api/exchanges/" + id + "/settlements"
                ))
                .body(response);
    }

    @GetMapping("/{id}/settlements")
    public List<ExchangeSettlementResponse> getSettlements(
            @PathVariable UUID id
    ) {
        return settlementService.getSettlements(id)
                .stream()
                .map(ExchangeSettlementResponse::from)
                .toList();
    }

    private static String actor() {
        return SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
    }
}