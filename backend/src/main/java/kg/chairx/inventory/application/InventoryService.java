package kg.chairx.inventory.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import kg.chairx.audit.AuditService;
import kg.chairx.inventory.api.ChangeBlockedStock;
import kg.chairx.inventory.api.ChangeReservedStock;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.api.StockMovementPage;
import kg.chairx.inventory.domain.InventoryBalance;
import kg.chairx.inventory.domain.StockMovement;
import kg.chairx.inventory.persistence.InventoryRepository;
import kg.chairx.product.application.ProductVariantService;
import kg.chairx.warehouse.application.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository repository;
    private final WarehouseService warehouses;
    private final ProductVariantService variants;
    private final AuditService audit;

    public InventoryService(
            InventoryRepository repository,
            WarehouseService warehouses,
            ProductVariantService variants,
            AuditService audit
    ) {
        this.repository = repository;
        this.warehouses = warehouses;
        this.variants = variants;
        this.audit = audit;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockMovement recordMovement(
            @NotNull @Valid RecordStockMovement command
    ) {
        var existing = repository.findMovement(
                command.operationId()
        );

        if (existing.isPresent()) {
            return replay(existing.get(), command);
        }

        requireReferences(
                command.warehouseId(),
                command.productVariantId()
        );

        var before = repository.lockOrCreate(
                command.warehouseId(),
                command.productVariantId()
        );

        // Another request may have committed this operation
        // while we waited for the balance lock.
        existing = repository.findMovement(
                command.operationId()
        );

        if (existing.isPresent()) {
            return replay(existing.get(), command);
        }

        var after = before.apply(
                command.type(),
                command.quantity()
        );

        var inserted = repository.insertMovement(command);

        if (inserted.isEmpty()) {
            // The same key raced on a different balance.
            // No stock has changed in this transaction yet.
            return replay(
                    repository.findMovement(
                            command.operationId()
                    ).orElseThrow(),
                    command
            );
        }

        var movement = inserted.get();

        repository.updateOnHand(after);

        audit.recordAs(
                command.actor() == null
                        ? "SYSTEM"
                        : command.actor(),
                "STOCK_MOVEMENT",
                movement.id(),
                command.type().name(),
                before,
                Map.of(
                        "balance",
                        after,
                        "movement",
                        movement
                )
        );

        return movement;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalance reserve(
            @NotNull @Valid ChangeReservedStock command
    ) {
        requireReferences(
                command.warehouseId(),
                command.productVariantId()
        );

        InventoryBalance current = repository.lockOrCreate(
                command.warehouseId(),
                command.productVariantId()
        );

        InventoryBalance changed = current.reserve(
                command.quantity()
        );

        repository.updateReserved(changed);

        return changed;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalance releaseReservation(
            @NotNull @Valid ChangeReservedStock command
    ) {
        InventoryBalance current = repository.lockOrCreate(
                command.warehouseId(),
                command.productVariantId()
        );

        InventoryBalance changed = current.releaseReservation(
                command.quantity()
        );

        repository.updateReserved(changed);

        return changed;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalance block(
            ChangeBlockedStock command
    ) {
        InventoryBalance current = repository.lockOrCreate(
                command.warehouseId(),
                command.productVariantId()
        );

        InventoryBalance changed = current.block(
                command.quantity()
        );

        repository.updateBlocked(changed);

        return changed;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalance unblock(
            ChangeBlockedStock command
    ) {
        InventoryBalance current = repository.lockOrCreate(
                command.warehouseId(),
                command.productVariantId()
        );

        InventoryBalance changed = current.unblock(
                command.quantity()
        );

        repository.updateBlocked(changed);

        return changed;
    }

    public InventoryBalance getBalance(
            @NotNull UUID warehouseId,
            @NotNull UUID variantId
    ) {
        requireReferences(warehouseId, variantId);

        return repository.findBalance(
                warehouseId,
                variantId
        ).orElseGet(
                () -> new InventoryBalance(
                        warehouseId,
                        variantId,
                        0,
                        0,
                        0
                )
        );
    }

    public StockMovementPage listMovements(
            @NotNull UUID warehouseId,
            @NotNull UUID variantId,
            @Min(
                    value = 0,
                    message = "Номер страницы не может быть отрицательным"
            )
            int page,
            @Min(
                    value = 1,
                    message = "Размер страницы должен быть положительным"
            )
            @Max(
                    value = 100,
                    message = "Размер страницы не может превышать 100"
            )
            int size
    ) {
        requireReferences(warehouseId, variantId);

        return new StockMovementPage(
                repository.listMovements(
                        warehouseId,
                        variantId,
                        page,
                        size
                ),
                page,
                size,
                repository.countMovements(
                        warehouseId,
                        variantId
                )
        );
    }

    private void requireReferences(
            UUID warehouseId,
            UUID variantId
    ) {
        warehouses.get(warehouseId);
        variants.get(variantId);
    }

    private StockMovement replay(
            StockMovement movement,
            RecordStockMovement command
    ) {
        if (!movement.warehouseId().equals(
                command.warehouseId()
        )
                || !movement.productVariantId().equals(
                command.productVariantId()
        )
                || movement.type() != command.type()
                || movement.quantity() != command.quantity()
                || !movement.sourceType().equals(
                command.sourceType()
        )
                || !movement.sourceId().equals(
                command.sourceId()
        )
                || !Objects.equals(
                movement.actor(),
                command.actor()
        )) {
            throw new InventoryOperationConflictException();
        }

        return movement;
    }
}