package kg.chairx.finance.api;

import jakarta.validation.Valid;
import kg.chairx.finance.application.FinanceTransferService;
import kg.chairx.finance.domain.FinanceAccount;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/transfers")
public class FinanceTransferController {
    private final FinanceTransferService transfers;

    public FinanceTransferController(FinanceTransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping
    public TransferResponse create(
            @Valid @RequestBody FinanceTransferRequest request,
            Authentication authentication
    ) {
        UUID id = transfers.transfer(request.transferId(), request.from(), request.to(),
                request.amount(), authentication.getName());
        return new TransferResponse(id, request.from(), request.to(), request.amount());
    }

    public record TransferResponse(
            UUID transferId, FinanceAccount from, FinanceAccount to, BigDecimal amount
    ) {}
}
