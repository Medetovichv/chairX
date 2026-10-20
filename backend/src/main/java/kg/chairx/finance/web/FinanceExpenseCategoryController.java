package kg.chairx.finance.web;

import kg.chairx.expense.application.ExpenseAccrualService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/finance/expense-categories")
public class FinanceExpenseCategoryController {
    private final JdbcClient jdbc;
    private final ExpenseAccrualService accruals;
    public FinanceExpenseCategoryController(JdbcClient jdbc, ExpenseAccrualService accruals) {
        this.jdbc=jdbc; this.accruals=accruals;
    }
    public record CategoryAmount(String category,BigDecimal accrued,BigDecimal direct,BigDecimal total) {}
    public record Categories(LocalDate from,LocalDate to,List<CategoryAmount> categories,BigDecimal grandTotal) {}

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public Categories report(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        if(from==null||to==null||!from.isBefore(to)
                ||java.time.temporal.ChronoUnit.DAYS.between(from,to)>93)
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid report period");
        Map<String,BigDecimal> accrued=new TreeMap<>();
        for(var line:accruals.report(from,to)){
            accrued.merge(line.accrual().category().name(),line.recognized(),BigDecimal::add);
        }
        Map<String,BigDecimal> direct=new TreeMap<>();
        jdbc.sql("""
            SELECT e.category,COALESCE(SUM(e.amount),0) AS value
            FROM expenses e
            WHERE e.expense_date>=:from AND e.expense_date<:to
              AND NOT EXISTS(SELECT 1 FROM expense_accrual_payments ap WHERE ap.expense_id=e.id)
            GROUP BY e.category
            """).param("from",from).param("to",to)
            .query((rs,i)->Map.entry(rs.getString("category"),rs.getBigDecimal("value")))
            .list().forEach(entry->direct.put(entry.getKey(),entry.getValue()));
        Set<String> keys=new TreeSet<>(accrued.keySet());
        keys.addAll(direct.keySet());
        List<CategoryAmount> entries=keys.stream().map(name->{
            BigDecimal a=accrued.getOrDefault(name,BigDecimal.ZERO);
            BigDecimal d=direct.getOrDefault(name,BigDecimal.ZERO);
            return new CategoryAmount(name,a,d,a.add(d));
        }).toList();
        return new Categories(from,to,entries,entries.stream()
                .map(CategoryAmount::total).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
}
