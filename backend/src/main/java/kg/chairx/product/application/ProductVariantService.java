package kg.chairx.product.application;

import jakarta.validation.Valid;
import java.util.UUID;
import kg.chairx.audit.AuditService;
import kg.chairx.product.api.*;
import kg.chairx.product.domain.ProductVariant;
import kg.chairx.product.persistence.ProductVariantRepository;
import kg.chairx.product.persistence.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
public class ProductVariantService {
    private final ProductVariantRepository repository;
    private final AuditService audit;

    private final ProductRepository products;

    public ProductVariantService(ProductVariantRepository repository, AuditService audit, ProductRepository products) {
        this.repository = repository;
        this.audit = audit;
        this.products = products;
    }

    @Transactional
    public ProductVariantResponse create(UUID productId, @Valid CreateProductVariantRequest request) {
        var product = products.findById(productId).orElseThrow(ProductNotFoundException::new);
        var entity = repository.saveAndFlush(new ProductVariant(product, request.name(),
                request.sku(), request.color(), request.recommendedSalePrice()));
        var response = ProductMapper.response(entity);
        audit.record("PRODUCT_VARIANT", entity.getId(), "CREATED", null, response);
        return response;
    }

    public ProductVariantResponse get(UUID id) {
        return ProductMapper.response(require(id));
    }

    public ProductVariantPageResponse list(UUID productId, int page, int size) {
        if (!products.existsById(productId)) {
            throw new ProductNotFoundException();
        }
        var paging = PageRequest.of(page, size, Sort.by("id"));
        var result = repository.findByProduct_Id(productId, paging);
        return new ProductVariantPageResponse(result.getContent().stream().map(ProductMapper::response).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ProductVariantResponse update(UUID id, @Valid UpdateProductVariantRequest request) {
        var entity = require(id);
        var before = ProductMapper.response(entity);
        entity.update(request.name(), request.sku(), request.color(), request.recommendedSalePrice());
        repository.flush();
        var after = ProductMapper.response(entity);
        if (!before.equals(after)) {
            audit.record("PRODUCT_VARIANT", id, "UPDATED", before, after);
        }
        return after;
    }

    @Transactional
    public ProductVariantResponse activate(UUID id) { return changeActive(id, true); }

    @Transactional
    public ProductVariantResponse deactivate(UUID id) { return changeActive(id, false); }

    private ProductVariantResponse changeActive(UUID id, boolean active) {
        var entity = require(id);
        var before = ProductMapper.response(entity);
        if (entity.isActive() == active) { return before; }
        entity.setActive(active);
        repository.flush();
        var after = ProductMapper.response(entity);
        audit.record("PRODUCT_VARIANT", id, active ? "ACTIVATED" : "DEACTIVATED", before, after);
        return after;
    }

    private ProductVariant require(UUID id) {
        return repository.findById(id).orElseThrow(ProductVariantNotFoundException::new);
    }
}
