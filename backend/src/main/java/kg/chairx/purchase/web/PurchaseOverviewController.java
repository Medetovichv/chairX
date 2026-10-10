package kg.chairx.purchase.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kg.chairx.purchase.api.PurchaseOverviewPage;
import kg.chairx.purchase.application.PurchaseOverviewService;
import kg.chairx.purchase.domain.PurchaseStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchases/overview")
public class PurchaseOverviewController {
    private final PurchaseOverviewService overview;

    public PurchaseOverviewController(PurchaseOverviewService overview) {
        this.overview = overview;
    }

    @GetMapping
    public PurchaseOverviewPage list(
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String number,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return overview.list(status,supplierId,from,to,number,page,size);
    }
}
