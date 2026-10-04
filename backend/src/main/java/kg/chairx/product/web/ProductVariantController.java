package kg.chairx.product.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import kg.chairx.product.api.*;
import kg.chairx.product.application.ProductVariantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProductVariantController {
    private final ProductVariantService service;

    public ProductVariantController(ProductVariantService service) { this.service = service; }

    @PostMapping("/products/{productId}/variants")
    public ResponseEntity<ProductVariantResponse> create(@PathVariable UUID productId,
            @Valid @RequestBody CreateProductVariantRequest request) {
        var response = service.create(productId, request);
        return ResponseEntity.created(URI.create("/api/product-variants/" + response.id())).body(response);
    }

    @GetMapping("/products/{productId}/variants")
    public ProductVariantPageResponse list(@PathVariable UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(productId, page, size);
    }

    @GetMapping("/product-variants/{id}")
    public ProductVariantResponse get(@PathVariable UUID id) { return service.get(id); }

    @PutMapping("/product-variants/{id}")
    public ProductVariantResponse update(@PathVariable UUID id,
            @Valid @RequestBody UpdateProductVariantRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/product-variants/{id}/activate")
    public ProductVariantResponse activate(@PathVariable UUID id) { return service.activate(id); }

    @PostMapping("/product-variants/{id}/deactivate")
    public ProductVariantResponse deactivate(@PathVariable UUID id) { return service.deactivate(id); }
}
