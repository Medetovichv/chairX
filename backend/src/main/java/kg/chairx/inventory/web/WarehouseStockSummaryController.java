package kg.chairx.inventory.web;

import kg.chairx.inventory.api.WarehouseStockSummary;
import kg.chairx.inventory.application.WarehouseStockSummaryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/warehouse-summary")
public class WarehouseStockSummaryController {
    private final WarehouseStockSummaryService summary;

    public WarehouseStockSummaryController(WarehouseStockSummaryService summary) {
        this.summary = summary;
    }

    @GetMapping
    public List<WarehouseStockSummary> overview() {
        return summary.overview();
    }
}
