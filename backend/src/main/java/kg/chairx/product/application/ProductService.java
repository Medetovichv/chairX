package kg.chairx.product.application;

import jakarta.validation.Valid;
import java.util.UUID;
import java.util.Locale;
import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
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
        return list(page, size, null, null, null);
    }

    public ProductPageResponse list(int page, int size, String search, String category, Boolean active) {
        var paging = PageRequest.of(page, size, Sort.by("id"));
        String query = normalize(search, 200);
        String requestedCategory = normalize(category, 120);
        Specification<Product> specification = (root, criteria, builder) -> {
            var clauses = new ArrayList<Predicate>();
            if (query != null) {
                String mask = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
                clauses.add(builder.or(
                        builder.like(builder.lower(root.get("name")), mask, '\\'),
                        builder.like(builder.lower(root.get("category")), mask, '\\')));
            }
            if (requestedCategory != null) {
                clauses.add(builder.equal(builder.lower(root.get("category")),
                        requestedCategory.toLowerCase(Locale.ROOT)));
            }
            if (active != null) clauses.add(builder.equal(root.get("active"), active));
            return builder.and(clauses.toArray(Predicate[]::new));
        };
        var result = repository.findAll(specification, paging);
        return new ProductPageResponse(result.getContent().stream().map(ProductMapper::response).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException("Слишком длинный параметр поиска каталога");
        }
        return stripped;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
