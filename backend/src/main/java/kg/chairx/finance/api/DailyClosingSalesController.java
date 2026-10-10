package kg.chairx.finance.api;

import kg.chairx.finance.application.DailyClosingSalesSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

/** Operational completed sales only; never financial balances or ledger postings. */
@RestController
@RequestMapping("/api/finance/closings")
public class DailyClosingSalesController {
    private final JdbcClient jdbc;
    public DailyClosingSalesController(JdbcClient jdbc) { this.jdbc=jdbc; }

    @GetMapping("/{date}/sales")
    @Transactional(readOnly = true)
    public DailyClosingSalesSnapshot.Result list(@PathVariable LocalDate date) {
        return DailyClosingSalesSnapshot.read(jdbc,date);
    }
}
