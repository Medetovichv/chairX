package kg.chairx.sale.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import kg.chairx.sale.api.CreateSaleCommentRequest;
import kg.chairx.sale.api.SaleCommentResponse;
import kg.chairx.sale.api.SaleCommentsPage;
import kg.chairx.sale.application.SaleCommentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales/{saleId}/comments")
public class SaleCommentsController {
    private final SaleCommentService service;

    public SaleCommentsController(SaleCommentService service) { this.service=service; }

    @GetMapping
    public SaleCommentsPage list(@PathVariable UUID saleId,
        @RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.list(saleId,page,size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleCommentResponse create(@PathVariable UUID saleId,
            @Valid @RequestBody CreateSaleCommentRequest request) {
        return service.create(saleId,request);
    }
}
