package kg.chairx.expense.web;

import kg.chairx.expense.application.ExpenseAccrualService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/expenses/accruals")
public class ExpenseAccrualController {
    private final ExpenseAccrualService service;
    public ExpenseAccrualController(ExpenseAccrualService service) { this.service=service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EXPENSES_CREATE')")
    public ExpenseAccrualService.Accrual create(
            @RequestBody ExpenseAccrualService.Create request, Authentication authentication) {
        return service.create(request,authentication.getName());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXPENSES_READ')")
    public List<ExpenseAccrualService.ReportLine> report(
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return service.report(from,to);
    }
}
