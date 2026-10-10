package kg.chairx.inventory.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kg.chairx.inventory.api.InventoryOverviewPage;
import kg.chairx.inventory.application.InventoryOverviewService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/overview")
public class InventoryOverviewController {
    private final InventoryOverviewService overview;

    public InventoryOverviewController(InventoryOverviewService overview) {
        this.overview = overview;
    }

    @GetMapping
    public InventoryOverviewPage list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String variation,
            @RequestParam(defaultValue = "false") boolean includeZero,
            @RequestParam(defaultValue = "false") boolean onlyAvailable,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return overview.list(warehouseId, model, variation, includeZero, onlyAvailable, page, size);
    }
}
