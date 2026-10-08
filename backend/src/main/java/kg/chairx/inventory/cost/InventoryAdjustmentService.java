package kg.chairx.inventory.cost;

import java.math.BigDecimal;
import java.util.UUID;

import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.domain.StockMovementType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryAdjustmentService {

    private final InventoryService inventory;
    private final InventoryCostService costs;
    private final InventoryCostRepository repository;

    public InventoryAdjustmentService(
            InventoryService inventory,
            InventoryCostService costs,
            InventoryCostRepository repository
    ) {
        this.inventory = inventory;
        this.costs = costs;
        this.repository = repository;
    }

    @Transactional
    public void recordValuedAdjustmentIn(
            UUID operationId,
            UUID warehouseId,
            UUID variantId,
            long quantity,
            BigDecimal totalCost,
            String actor
    ) {
        if (operationId == null
                || warehouseId == null
                || variantId == null
                || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Invalid inventory adjustment"
            );
        }

        if (totalCost == null
                || totalCost.signum() < 0
                || totalCost.scale() > 2) {
            throw new IllegalArgumentException(
                    "Invalid adjustment cost"
            );
        }

        var movement = inventory.recordMovement(
                new RecordStockMovement(
                        operationId,
                        warehouseId,
                        variantId,
                        StockMovementType.ADJUSTMENT_IN,
                        quantity,
                        "VALUED_ADJUSTMENT",
                        operationId,
                        actor
                )
        );

        // Не создаём повторную партию при повторной операции.
        repository.lockMovement(movement.id());

        BigDecimal existingCost =
                repository.findOriginalCost(movement.id());

        if (existingCost != null) {
            if (existingCost.compareTo(totalCost) != 0) {
                throw new IllegalStateException(
                        "ADJUSTMENT_COST_CONFLICT: operationId="
                                + operationId
                );
            }

            return;
        }

        costs.recordValuedAdjustment(
                movement,
                totalCost
        );
    }
}