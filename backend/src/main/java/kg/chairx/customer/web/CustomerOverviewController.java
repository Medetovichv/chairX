package kg.chairx.customer.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kg.chairx.customer.api.CustomerOverviewPage;
import kg.chairx.customer.application.CustomerOverviewService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers/overview")
public class CustomerOverviewController {
    private final CustomerOverviewService service;
    public CustomerOverviewController(CustomerOverviewService service) {
        this.service=service;
    }
    @GetMapping
    public CustomerOverviewPage list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.list(query,page,size);
    }
}
