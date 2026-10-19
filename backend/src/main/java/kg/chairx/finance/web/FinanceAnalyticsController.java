package kg.chairx.finance.web;

import kg.chairx.finance.application.FinanceProfitabilityService;
import kg.chairx.finance.application.CashFlowService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Monthly management series: economic recognition and cash flow are separate.
 * Returns null for unverified operating profit instead of fabricating zeros.
 */
@RestController
@RequestMapping("/api/finance/analytics")
public class FinanceAnalyticsController {
    private static final ZoneId ZONE=ZoneId.of("Asia/Bishkek");
    private final FinanceProfitabilityService pnl;
    private final CashFlowService cash;
    public FinanceAnalyticsController(FinanceProfitabilityService pnl,CashFlowService cash){
        this.pnl=pnl;this.cash=cash;
    }
    public record Month(LocalDate from,BigDecimal recognizedRevenue,
            BigDecimal fifoCost,BigDecimal grossProfit,
            BigDecimal operatingProfit,boolean profitVerified,
            BigDecimal accrualExpenses,BigDecimal otherExpenses,
            BigDecimal cashIn,BigDecimal cashOut,BigDecimal netCash) {}
    public record MonthlyReport(LocalDate from,LocalDate to,List<Month> months) {}

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public MonthlyReport monthly(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){
        if(from==null||to==null||!from.isBefore(to)
                ||from.getDayOfMonth()!=1||to.getDayOfMonth()!=1
                ||ChronoUnit.MONTHS.between(from,to)>12){
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Use first-of-month boundaries over 1-12 months");
        }
        var list=new ArrayList<Month>();
        for(LocalDate month=from;month.isBefore(to);month=month.plusMonths(1)){
            LocalDate next=month.plusMonths(1);
            var p=pnl.calculate(month,next);
            var c=cash.summary(month.atStartOfDay(ZONE).toInstant(),
                               next.atStartOfDay(ZONE).toInstant());
            list.add(new Month(month,p.grossRevenue(),p.verifiedFifoCost(),p.grossProfit(),
                    p.operatingProfit(),p.netProfitAvailable(),p.recognizedAccruals(),
                    p.otherOperatingExpenses(),c.totalIn(),c.totalOut(),c.netCashFlow()));
        }
        return new MonthlyReport(from,to,List.copyOf(list));
    }
}
