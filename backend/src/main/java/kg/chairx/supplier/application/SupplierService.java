package kg.chairx.supplier.application;

import jakarta.validation.Valid;
import java.util.UUID;
import kg.chairx.audit.AuditService;
import kg.chairx.supplier.api.*;
import kg.chairx.supplier.domain.Supplier;
import kg.chairx.supplier.persistence.SupplierRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
public class SupplierService {
    private final SupplierRepository repository;
    private final AuditService audit;

    public SupplierService(SupplierRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;

    }

    @Transactional
    public SupplierResponse create(@Valid CreateSupplierRequest request) {

        var entity = repository.saveAndFlush(new Supplier(request.name(), request.contactInformation(), request.comment()));
        var response = SupplierMapper.response(entity);
        audit.record("SUPPLIER", entity.getId(), "CREATED", null, response);
        return response;
    }

    public SupplierResponse get(UUID id) {
        return SupplierMapper.response(require(id));
    }

    public SupplierPageResponse list(int page, int size) {

        var paging = PageRequest.of(page, size, Sort.by("id"));
        var result = repository.findAll(paging);
        return new SupplierPageResponse(result.getContent().stream().map(SupplierMapper::response).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public SupplierResponse update(UUID id, @Valid UpdateSupplierRequest request) {
        var entity = require(id);
        var before = SupplierMapper.response(entity);
        entity.update(request.name(), request.contactInformation(), request.comment());
        repository.flush();
        var after = SupplierMapper.response(entity);
        if (!before.equals(after)) {
            audit.record("SUPPLIER", id, "UPDATED", before, after);
        }
        return after;
    }

    @Transactional
    public SupplierResponse activate(UUID id) { return changeActive(id, true); }

    @Transactional
    public SupplierResponse deactivate(UUID id) { return changeActive(id, false); }

    private SupplierResponse changeActive(UUID id, boolean active) {
        var entity = require(id);
        var before = SupplierMapper.response(entity);
        if (entity.isActive() == active) { return before; }
        entity.setActive(active);
        repository.flush();
        var after = SupplierMapper.response(entity);
        audit.record("SUPPLIER", id, active ? "ACTIVATED" : "DEACTIVATED", before, after);
        return after;
    }

    private Supplier require(UUID id) {
        return repository.findById(id).orElseThrow(SupplierNotFoundException::new);
    }
}
