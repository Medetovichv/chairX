package kg.chairx.inventory.web;

import kg.chairx.inventory.api.InventoryBalancePage;
import kg.chairx.inventory.api.StockMovementPage;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.api.InventoryBalanceResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryQueryController {
    private final InventoryService inventory;
    public InventoryQueryController(InventoryService inventory) { this.inventory=inventory; }

    @GetMapping("/balances")
    public InventoryBalancePage balances(@RequestParam UUID warehouseId,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return inventory.listBalances(warehouseId,page,size);
    }

    @GetMapping("/balances/{warehouseId}/{variantId}")
    public InventoryBalanceResponse balance(@PathVariable UUID warehouseId,@PathVariable UUID variantId) {
        return InventoryBalanceResponse.from(inventory.getBalance(warehouseId,variantId));
    }

    @GetMapping("/movements")
    public StockMovementPage movements(@RequestParam UUID warehouseId,
            @RequestParam UUID variantId,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return inventory.listMovements(warehouseId,variantId,page,size);
    }
}
