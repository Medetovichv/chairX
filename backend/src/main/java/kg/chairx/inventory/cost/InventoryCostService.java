package kg.chairx.inventory.cost;

import java.math.BigDecimal;
import java.util.UUID;
import kg.chairx.inventory.domain.StockMovement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.RoundingMode;
import kg.chairx.inventory.domain.StockMovementType;

@Service
public class InventoryCostService {

    private final InventoryCostRepository repository;

    public InventoryCostService(InventoryCostRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal consumeSale(
            StockMovement movement
    ) {
        if (movement.type() != StockMovementType.SALE_OUT) {
            throw new IllegalArgumentException(
                    "FIFO consumption requires SALE_OUT movement"
            );
        }

        return consume(movement, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal consumeWriteOff(StockMovement movement, UUID receiptItemId) {
        if (movement.type() != StockMovementType.WRITE_OFF) {
            throw new IllegalArgumentException("Expected WRITE_OFF");
        }
        return consume(movement, receiptItemId);
    }

    private BigDecimal consume(StockMovement movement, UUID receiptItemId) {
        long remainingQuantity = movement.quantity();

        if (remainingQuantity <= 0) {
            throw new IllegalArgumentException(
                    "FIFO quantity must be positive"
            );
        }
        repository.lockMovement(movement.id());
        if (repository.hasAllocations(movement.id())) {
            throw new IllegalStateException(
                    "FIFO_ALREADY_ALLOCATED: " + movement.id()
            );
        }
        var layers = receiptItemId == null
                ? repository.lockAvailableLayers(movement.warehouseId(), movement.productVariantId())
                : repository.lockReceiptLayer(movement.warehouseId(), movement.productVariantId(), receiptItemId);

        long availableQuantity = 0;

        for (var layer : layers) {
            availableQuantity = Math.addExact(
                    availableQuantity,
                    layer.quantityRemaining()
            );
        }

        if (availableQuantity < movement.quantity()) {
            throw new InventoryCostException("FIFO_COST_LAYERS_INSUFFICIENT",
                    "FIFO_COST_LAYERS_INSUFFICIENT: required="
                            + movement.quantity()
                            + ", available="
                            + availableQuantity
            );
        }

        BigDecimal totalCost = BigDecimal.ZERO.setScale(2);

        for (var layer : layers) {
            if (remainingQuantity == 0) {
                break;
            }

            long quantity = Math.min(
                    remainingQuantity,
                    layer.quantityRemaining()
            );

            BigDecimal allocatedCost;

            if (quantity == layer.quantityRemaining()) {
                // Последнее списание забирает весь остаток стоимости.
                // Это исключает потерю копеек при округлении.
                allocatedCost = layer.remainingCost();
            } else {
                allocatedCost = layer.remainingCost()
                        .multiply(BigDecimal.valueOf(quantity))
                        .divide(
                                BigDecimal.valueOf(layer.quantityRemaining()),
                                2,
                                RoundingMode.HALF_UP
                        );
            }

            repository.consumeLayer(
                    layer.id(),
                    quantity,
                    allocatedCost
            );

            repository.recordConsumption(
                    movement.id(),
                    layer.id(),
                    quantity,
                    allocatedCost
            );

            totalCost = totalCost.add(allocatedCost);
            remainingQuantity -= quantity;
        }

        if (remainingQuantity != 0) {
            throw new InventoryCostException("FIFO_COST_LAYERS_INSUFFICIENT",
                    "FIFO_COST_LAYERS_INSUFFICIENT: missing "
                            + remainingQuantity
                            + " units for warehouse "
                            + movement.warehouseId()
                            + ", variant "
                            + movement.productVariantId()
            );
        }

        return totalCost;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordValuedAdjustment(
            StockMovement movement,
            BigDecimal totalCost
    ) {
        if (movement.type() != StockMovementType.ADJUSTMENT_IN) {
            throw new IllegalArgumentException(
                    "Cost initialization requires ADJUSTMENT_IN"
            );
        }

        if (movement.quantity() <= 0) {
            throw new IllegalArgumentException(
                    "Adjustment quantity must be positive"
            );
        }

        if (totalCost == null
                || totalCost.scale() > 2
                || totalCost.signum() < 0) {
            throw new IllegalArgumentException(
                    "Adjustment cost must be non-negative with at most 2 decimals"
            );
        }

        repository.lockMovement(movement.id());

        if (repository.hasCostLayer(movement.id())) {
            throw new IllegalStateException(
                    "ADJUSTMENT_ALREADY_VALUED: " + movement.id()
            );
        }

        repository.createReceiptLayer(
                movement.id(),
                movement.warehouseId(),
                movement.productVariantId(),
                movement.quantity(),
                totalCost,
                movement.occurredAt()
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPurchaseReceipt(
            StockMovement movement,
            UUID warehouseId,
            UUID variantId,
            long quantity,
            BigDecimal totalCost
    ) {
        if (movement.type() != StockMovementType.PURCHASE_IN
                || !movement.warehouseId().equals(warehouseId)
                || !movement.productVariantId().equals(variantId)
                || movement.quantity() != quantity) {
            throw new IllegalArgumentException(
                    "Purchase receipt and stock movement do not match"
            );
        }

        if (totalCost == null || totalCost.signum() < 0 || totalCost.scale() > 2) {
            throw new IllegalArgumentException(
                    "Purchase receipt cost must be non-negative"
            );
        }

        repository.createReceiptLayer(
                movement.id(),
                warehouseId,
                variantId,
                quantity,
                totalCost,
                movement.occurredAt()
        );
    }
}