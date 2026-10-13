package kg.chairx.finance.api;

import kg.chairx.finance.application.EmployeeDailyClosingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

/** The actor is the authenticated account, never a user id supplied by the client. */
@RestController
@RequestMapping("/api/finance/my-closings")
public class EmployeeDailyClosingController {
    private final EmployeeDailyClosingService service;

    public EmployeeDailyClosingController(EmployeeDailyClosingService service) {
        this.service=service;
    }

    @GetMapping
    public List<LocalDate> dates(Authentication auth) {
        return service.companyDates(auth.getName());
    }

    @GetMapping("/{date}")
    public EmployeeDailyClosingService.Report get(@PathVariable LocalDate date, Authentication auth) {
        return service.companyReport(auth.getName(),date);
    }

    @PostMapping("/{date}")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeDailyClosingService.Report close(
            @PathVariable LocalDate date,
            @RequestBody EmployeeDailyClosingService.Request body,
            Authentication auth) {
        return service.closeCompany(auth.getName(),date,body);
    }
}
