package kg.chairx.inventory.cost;

import java.math.BigDecimal;

import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.domain.StockMovementType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryCostPostingService {

    private final InventoryService inventory;
    private final InventoryCostService costs;

    public InventoryCostPostingService(
            InventoryService inventory,
            InventoryCostService costs
    ) {
        this.inventory = inventory;
        this.costs = costs;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public BigDecimal postSaleOut(RecordStockMovement command) {
        if (command.type() != StockMovementType.SALE_OUT) {
            throw new IllegalArgumentException(
                    "Expected SALE_OUT movement"
            );
        }

        var movement = inventory.recordMovement(command);

        return costs.consumeSale(movement);
    }
}