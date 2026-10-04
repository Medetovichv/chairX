package kg.chairx.warehouse.application;

import jakarta.validation.Valid;
import java.util.UUID;
import kg.chairx.audit.AuditService;
import kg.chairx.warehouse.api.CreateWarehouseRequest;
import kg.chairx.warehouse.api.UpdateWarehouseRequest;
import kg.chairx.warehouse.api.WarehousePageResponse;
import kg.chairx.warehouse.api.WarehouseResponse;
import kg.chairx.warehouse.domain.Warehouse;
import kg.chairx.warehouse.persistence.WarehouseRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
public class WarehouseService {
    private final WarehouseRepository repository;
    private final AuditService audit;

    public WarehouseService(WarehouseRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    public WarehouseResponse create(@Valid CreateWarehouseRequest request) {
        Warehouse warehouse;
        try {
            warehouse = repository.saveAndFlush(new Warehouse(request.name(), request.code(), request.address()));
        } catch (DataIntegrityViolationException exception) {
            // The database decides uniqueness, including simultaneous requests and inactive warehouses.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "uq_warehouses_code".equals(constraint.getConstraintName())) {
                    throw new WarehouseCodeAlreadyExistsException();
                }
            }
            throw exception;
        }
        var result = response(warehouse);
        audit.record("WAREHOUSE", warehouse.getId(), "CREATED", null, result);
        return result;
    }

    public WarehouseResponse get(UUID id) { return response(require(id)); }

    public WarehousePageResponse list(int page, int size) {
        var result = repository.findAll(PageRequest.of(page, size, Sort.by("code")));
        return new WarehousePageResponse(result.getContent().stream().map(WarehouseService::response).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public WarehouseResponse update(UUID id, @Valid UpdateWarehouseRequest request) {
        var warehouse = require(id);
        var before = response(warehouse);
        warehouse.update(request.name(), request.address());
        repository.flush();
        var after = response(warehouse);
        if (!before.equals(after)) {
            audit.record("WAREHOUSE", id, "UPDATED", before, after);
        }
        return after;
    }

    @Transactional
    public WarehouseResponse activate(UUID id) { return changeActive(id, true); }

    @Transactional
    public WarehouseResponse deactivate(UUID id) { return changeActive(id, false); }

    private WarehouseResponse changeActive(UUID id, boolean active) {
        var warehouse = require(id);
        var before = response(warehouse);
        if (warehouse.isActive() == active) { return before; }
        if (active) { warehouse.activate(); } else { warehouse.deactivate(); }
        repository.flush();
        var after = response(warehouse);
        audit.record("WAREHOUSE", id, active ? "ACTIVATED" : "DEACTIVATED", before, after);
        return after;
    }

    private Warehouse require(UUID id) {
        return repository.findById(id).orElseThrow(WarehouseNotFoundException::new);
    }

    private static WarehouseResponse response(Warehouse warehouse) {
        return new WarehouseResponse(warehouse.getId(), warehouse.getName(), warehouse.getCode(),
                warehouse.getAddress(), warehouse.isActive(), warehouse.getCreatedAt(), warehouse.getUpdatedAt());
    }
}
