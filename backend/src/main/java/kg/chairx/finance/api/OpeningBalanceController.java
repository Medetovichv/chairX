package kg.chairx.finance.api;

import jakarta.validation.Valid;
import kg.chairx.finance.application.FinanceOpeningBalanceService;
import kg.chairx.finance.application.FinanceQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance/opening-balances")
public class OpeningBalanceController {
    private final FinanceOpeningBalanceService openings;
    private final FinanceQueryService queries;

    public OpeningBalanceController(FinanceOpeningBalanceService openings, FinanceQueryService queries) {
        this.openings = openings;
        this.queries = queries;
    }

    @PostMapping
    public FinanceAccountResponse initialize(
            @Valid @RequestBody OpeningBalanceRequest request,
            Authentication authentication
    ) {
        openings.initialize(request.account(), request.amount(), authentication.getName());
        return queries.getAccounts().stream()
                .filter(snapshot -> snapshot.code().equals(request.account().name()))
                .findFirst().orElseThrow();
    }
}
