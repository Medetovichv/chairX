package kg.chairx.finance.api;

import kg.chairx.finance.application.FinanceQueryService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/finance/accounts")
public class FinanceAccountController {

    private final FinanceQueryService financeQueryService;

    public FinanceAccountController(FinanceQueryService financeQueryService) {
        this.financeQueryService = financeQueryService;
    }

    @GetMapping
    public ResponseEntity<List<FinanceAccountResponse>> getAccounts() {
        return ResponseEntity.ok(financeQueryService.getAccounts());
    }
}
