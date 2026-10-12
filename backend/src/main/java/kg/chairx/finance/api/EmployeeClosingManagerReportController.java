package kg.chairx.finance.api;

import kg.chairx.finance.application.EmployeeClosingManagerReportService;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

/** URL deliberately separate from employee-only /my-closings. */
@RestController
@RequestMapping("/api/finance/employee-closings")
public class EmployeeClosingManagerReportController {
    private final EmployeeClosingManagerReportService service;
    public EmployeeClosingManagerReportController(EmployeeClosingManagerReportService service){
        this.service=service;
    }
    @GetMapping("/{date}")
    public EmployeeClosingManagerReportService.ManagerReport report(@PathVariable LocalDate date){
        return service.report(date);
    }
}
