package kg.chairx.finance.api;

import jakarta.validation.Valid;
import kg.chairx.expense.api.CreateExpenseRequest;
import kg.chairx.expense.api.ExpenseResponse;
import kg.chairx.expense.application.ExpenseService;
import kg.chairx.finance.application.DailyClosingAccessService;
import kg.chairx.finance.application.DailyClosingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/closings")
public class DailyClosingController {
    private final DailyClosingService service;
    private final DailyClosingAccessService access;
    private final ExpenseService expenses;

    public DailyClosingController(DailyClosingService service,
                                  DailyClosingAccessService access,
                                  ExpenseService expenses) {
        this.service = service;
        this.access = access;
        this.expenses = expenses;
    }

    @PostMapping("/{date}")
    @ResponseStatus(HttpStatus.CREATED)
    public DailyClosingResponse close(@PathVariable LocalDate date,
                                      @RequestBody DailyClosingRequest request,
                                      Authentication authentication) {
        return service.closeAuthorized(date, request, authentication.getName());
    }

    @PutMapping("/{date}")
    public DailyClosingResponse update(@PathVariable LocalDate date,
                                       @RequestBody DailyClosingUpdateRequest request,
                                       Authentication authentication) {
        return service.update(date, request, authentication.getName());
    }

    @GetMapping("/{date}")
    public DailyClosingResponse get(@PathVariable LocalDate date) {
        return service.findByDate(date);
    }

    @GetMapping
    public List<LocalDate> list() {
        return service.closedDates();
    }

    @GetMapping("/{date}/preview")
    public DailyClosingService.Preview preview(@PathVariable LocalDate date) {
        return service.preview(date);
    }

    @GetMapping("/{date}/access")
    public DailyClosingAccessService.AccessState access(@PathVariable LocalDate date) {
        return access.get(date);
    }

    public record UnlockRequest(String reason) {}

    @PostMapping("/{date}/unlock")
    public DailyClosingAccessService.Grant unlock(@PathVariable LocalDate date,
                                                   @RequestBody UnlockRequest request) {
        return access.unlock(date, request == null ? null : request.reason());
    }

    @PostMapping("/{date}/lock")
    public Map<String, Boolean> lock(@PathVariable LocalDate date) {
        return Map.of("revoked", access.revoke(date));
    }

    @GetMapping("/{date}/history")
    public List<DailyClosingService.AuditEntry> history(@PathVariable LocalDate date) {
        return service.history(date);
    }

    @PostMapping("/{date}/expenses")
    public ResponseEntity<ExpenseResponse> correction(@PathVariable LocalDate date,
                                                       @Valid @RequestBody CreateExpenseRequest request) {
        ExpenseResponse result = expenses.createForClosing(date, request);
        return ResponseEntity.created(URI.create("/api/expenses/" + result.id())).body(result);
    }
}
