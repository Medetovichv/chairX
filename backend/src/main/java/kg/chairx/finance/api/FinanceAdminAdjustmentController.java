package kg.chairx.finance.api;

import jakarta.validation.Valid;
import kg.chairx.finance.application.FinanceAdminAdjustmentService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance/accounts/adjustments")
public class FinanceAdminAdjustmentController {
    private final FinanceAdminAdjustmentService service;
    public FinanceAdminAdjustmentController(FinanceAdminAdjustmentService service) {
        this.service=service;
    }
    @PostMapping
    public FinanceAdminAdjustmentService.Result adjust(
            @Valid @RequestBody AdminBalanceAdjustmentRequest request,
            Authentication authentication) {
        return service.adjust(request,authentication.getName());
    }
}
