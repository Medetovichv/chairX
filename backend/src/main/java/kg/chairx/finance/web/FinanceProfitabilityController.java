package kg.chairx.finance.web;

import kg.chairx.finance.application.FinanceProfitabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/finance/profitability")
public class FinanceProfitabilityController {
    private final FinanceProfitabilityService service;
    public FinanceProfitabilityController(FinanceProfitabilityService service){this.service=service;}

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public FinanceProfitabilityService.Profitability report(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        if(from==null||to==null||!from.isBefore(to)
                ||java.time.temporal.ChronoUnit.DAYS.between(from,to)>93) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid report period");
        }
        return service.calculate(from,to);
    }
}
