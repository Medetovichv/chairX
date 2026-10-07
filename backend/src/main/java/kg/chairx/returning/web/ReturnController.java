package kg.chairx.returning.web;

import jakarta.validation.Valid;
import kg.chairx.returning.api.CreateReturnRequest;
import kg.chairx.returning.api.ReturnItemResponse;
import kg.chairx.returning.api.ReturnResponse;
import kg.chairx.returning.application.ReturnService;
import kg.chairx.returning.domain.Return;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/returns")
public class ReturnController {

    private final ReturnService service;

    public ReturnController(ReturnService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReturnResponse> create(
            @Valid @RequestBody CreateReturnRequest request
    ) {
        ReturnResponse response = toResponse(
                service.create(request)
        );

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/returns/" + response.id()
                        )
                )
                .body(response);
    }

    @GetMapping("/{id}")
    public ReturnResponse get(
            @PathVariable UUID id
    ) {
        return toResponse(
                service.get(id)
        );
    }

    private static ReturnResponse toResponse(
            Return value
    ) {
        return new ReturnResponse(
                value.id(),
                value.saleId(),
                value.warehouseId(),
                value.items()
                        .stream()
                        .map(item ->
                                new ReturnItemResponse(
                                        item.id(),
                                        item.saleItemId(),
                                        item.quantity(),
                                        item.condition()
                                )
                        )
                        .toList(),
                value.reason(),
                value.comment(),
                value.createdBy(),
                value.createdAt()
        );
    }
}