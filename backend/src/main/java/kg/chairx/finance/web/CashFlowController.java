package kg.chairx.finance.web;

import kg.chairx.finance.api.CashFlowSummaryResponse;
import kg.chairx.finance.application.CashFlowService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/finance")
public class CashFlowController {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Bishkek");

    private final CashFlowService service;

    public CashFlowController(CashFlowService service) {
        this.service = service;
    }

    @GetMapping("/cash-flow")
    public CashFlowSummaryResponse summary(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        Instant fromInstant = from
                .atStartOfDay(BUSINESS_ZONE)
                .toInstant();

        Instant toInstant = to
                .atStartOfDay(BUSINESS_ZONE)
                .toInstant();

        return service.summary(fromInstant, toInstant);
    }
}