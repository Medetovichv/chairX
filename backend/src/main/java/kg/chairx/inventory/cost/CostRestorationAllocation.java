package kg.chairx.inventory.cost;

import java.math.BigDecimal;
import java.util.UUID;

public record CostRestorationAllocation(UUID id, long quantity, BigDecimal amount,
        long returnedQuantity, BigDecimal returnedAmount) { }
