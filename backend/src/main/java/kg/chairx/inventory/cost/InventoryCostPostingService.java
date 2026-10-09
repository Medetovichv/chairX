package kg.chairx.inventory.cost;

import java.math.BigDecimal;
import java.util.UUID;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.domain.StockMovementType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic entry points for physical movements with historical valuation. */
@Service
@Transactional
public class InventoryCostPostingService {
    private final InventoryService inventory;
    private final InventoryCostService costs;
    private final InventoryCostRepository repository;
    private final InventoryCostRestorationService restorations;

    public InventoryCostPostingService(InventoryService inventory, InventoryCostService costs,
            InventoryCostRepository repository, InventoryCostRestorationService restorations) {
        this.inventory=inventory; this.costs=costs; this.repository=repository; this.restorations=restorations;
    }

    public BigDecimal postSaleOut(RecordStockMovement command) {
        requireType(command, StockMovementType.SALE_OUT);
        var movement = inventory.recordMovement(command); // Includes exact payload validation on replay.
        repository.lockMovement(movement.id());
        BigDecimal previous = repository.consumedCost(movement.id());
        return previous != null ? previous : costs.consumeSale(movement);
    }

    public BigDecimal postReturn(RecordStockMovement command, UUID saleItemId) {
        requireType(command, StockMovementType.RETURN_IN);
        UUID original = repository.saleMovement(saleItemId);
        repository.requireReturnOrigin(original, command.productVariantId());
        repository.requireNoUnvaluedReturns(saleItemId);
        var movement = inventory.recordMovement(command);
        return restorations.restore(movement, original);
    }

    public BigDecimal postWriteOff(RecordStockMovement command, UUID receiptItemId) {
        requireType(command, StockMovementType.WRITE_OFF);
        var movement = inventory.recordMovement(command);
        repository.lockMovement(movement.id());
        BigDecimal previous = repository.consumedCost(movement.id());
        if (previous != null) {
            if (!repository.sameWriteOffOrigin(movement.id(), receiptItemId)) {
                throw new InventoryCostException("WRITE_OFF_COST_ORIGIN_MISMATCH", "Изменена партия списания");
            }
            return previous;
        }
        repository.recordWriteOffOrigin(movement.id(), receiptItemId);
        return costs.consumeWriteOff(movement, receiptItemId);
    }

    private void requireType(RecordStockMovement command, StockMovementType type) {
        if (command == null || command.type() != type) throw new IllegalArgumentException("Expected " + type);
    }
}
