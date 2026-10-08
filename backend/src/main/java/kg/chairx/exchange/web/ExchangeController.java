package kg.chairx.exchange.web;

import jakarta.validation.Valid;
import kg.chairx.exchange.api.CreateExchangeRequest;
import kg.chairx.exchange.api.ExchangeResponse;
import kg.chairx.exchange.application.ExchangeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/exchanges")
public class ExchangeController {

    private final ExchangeService service;

    public ExchangeController(ExchangeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ExchangeResponse> create(
            @Valid @RequestBody CreateExchangeRequest request
    ) {
        ExchangeResponse response = service.create(request);

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/exchanges/" + response.id()
                        )
                )
                .body(response);
    }

    @GetMapping("/{id}")
    public ExchangeResponse get(
            @PathVariable UUID id
    ) {
        return service.get(id);
    }
}