package kg.chairx.finance.api;

import kg.chairx.finance.application.DailyClosingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance/closings")
public class DailyClosingController {
    private final DailyClosingService service;
    public DailyClosingController(DailyClosingService service) { this.service = service; }

    @PostMapping("/{date}")
    @ResponseStatus(HttpStatus.CREATED)
    public DailyClosingResponse close(@PathVariable LocalDate date,
                                      @RequestBody DailyClosingRequest request,
                                      Authentication authentication) {
        return service.close(date, request, authentication.getName());
    }

    @GetMapping("/{date}")
    public DailyClosingResponse get(@PathVariable LocalDate date) {
        return service.findByDate(date);
    }

    @GetMapping
    public List<LocalDate> list() { return service.closedDates(); }
}
