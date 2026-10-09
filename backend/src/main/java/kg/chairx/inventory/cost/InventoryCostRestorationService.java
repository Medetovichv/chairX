package kg.chairx.inventory.cost;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import kg.chairx.inventory.domain.StockMovement;
import kg.chairx.inventory.domain.StockMovementType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryCostRestorationService {
    private final InventoryCostRepository repository;
    public InventoryCostRestorationService(InventoryCostRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal restore(StockMovement returned, UUID originalMovement) {
        if (returned.type() != StockMovementType.RETURN_IN) throw new IllegalArgumentException("Expected RETURN_IN");
        repository.lockMovement(originalMovement);
        repository.requireReturnOrigin(originalMovement, returned.productVariantId());
        repository.lockMovement(returned.id());
        BigDecimal previous = repository.findOriginalCost(returned.id());
        if (previous != null) {
            if (!repository.sameRestorationOrigin(returned.id(), originalMovement)) {
                throw new InventoryCostException("RETURN_COST_ORIGIN_MISMATCH", "Изменён источник стоимости возврата");
            }
            return previous;
        }
        var allocations = repository.restorationAllocations(originalMovement);
        long available = 0;
        for (var allocation : allocations) available = Math.addExact(available, allocation.quantity() - allocation.returnedQuantity());
        if (available < returned.quantity()) throw new InventoryCostException("RETURN_COST_QUANTITY_EXCEEDED",
                "Количество превышает невозвращённый остаток исходного списания");
        long remaining = returned.quantity();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (var allocation : allocations) {
            long quantity = Math.min(remaining, allocation.quantity() - allocation.returnedQuantity());
            if (quantity == 0) continue;
            // Cumulative allocation conserves every original cent when the last units are returned.
            BigDecimal target = allocation.amount().multiply(BigDecimal.valueOf(allocation.returnedQuantity() + quantity))
                    .divide(BigDecimal.valueOf(allocation.quantity()), 2, RoundingMode.HALF_UP);
            BigDecimal amount = target.subtract(allocation.returnedAmount());
            repository.recordRestoration(returned.id(), allocation.id(), quantity, amount);
            total = total.add(amount);
            remaining -= quantity;
            if (remaining == 0) break;
        }
        repository.createReceiptLayer(returned.id(), returned.warehouseId(), returned.productVariantId(),
                returned.quantity(), total, returned.occurredAt());
        return total;
    }
}
