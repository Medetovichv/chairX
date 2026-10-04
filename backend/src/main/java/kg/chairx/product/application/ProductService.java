package kg.chairx.product.application;

import jakarta.validation.Valid;
import java.util.UUID;
import kg.chairx.audit.AuditService;
import kg.chairx.product.api.*;
import kg.chairx.product.domain.Product;
import kg.chairx.product.persistence.ProductRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository repository;
    private final AuditService audit;

    public ProductService(ProductRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;

    }

    @Transactional
    public ProductResponse create(@Valid CreateProductRequest request) {

        var entity = repository.saveAndFlush(new Product(request.name(), request.description(), request.category()));
        var response = ProductMapper.response(entity);
        audit.record("PRODUCT", entity.getId(), "CREATED", null, response);
        return response;
    }

    public ProductResponse get(UUID id) {
        return ProductMapper.response(require(id));
    }

    public ProductPageResponse list(int page, int size) {

        var paging = PageRequest.of(page, size, Sort.by("id"));
        var result = repository.findAll(paging);
        return new ProductPageResponse(result.getContent().stream().map(ProductMapper::response).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ProductResponse update(UUID id, @Valid UpdateProductRequest request) {
        var entity = require(id);
        var before = ProductMapper.response(entity);
        entity.update(request.name(), request.description(), request.category());
        repository.flush();
        var after = ProductMapper.response(entity);
        if (!before.equals(after)) {
            audit.record("PRODUCT", id, "UPDATED", before, after);
        }
        return after;
    }

    @Transactional
    public ProductResponse activate(UUID id) { return changeActive(id, true); }

    @Transactional
    public ProductResponse deactivate(UUID id) { return changeActive(id, false); }

    private ProductResponse changeActive(UUID id, boolean active) {
        var entity = require(id);
        var before = ProductMapper.response(entity);
        if (entity.isActive() == active) { return before; }
        entity.setActive(active);
        repository.flush();
        var after = ProductMapper.response(entity);
        audit.record("PRODUCT", id, active ? "ACTIVATED" : "DEACTIVATED", before, after);
        return after;
    }

    private Product require(UUID id) {
        return repository.findById(id).orElseThrow(ProductNotFoundException::new);
    }
}
