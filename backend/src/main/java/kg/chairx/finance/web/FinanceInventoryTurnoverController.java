package kg.chairx.finance.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Valuation reconstructed from immutable FIFO cost postings, at Bishkek period boundaries. */
@RestController
@RequestMapping("/api/finance/inventory-turnover")
public class FinanceInventoryTurnoverController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Bishkek");
    private final JdbcClient jdbc;
    public FinanceInventoryTurnoverController(JdbcClient jdbc) {this.jdbc=jdbc;}

    public record Turnover(LocalDate from, LocalDate to, BigDecimal openingCost,
                           BigDecimal closingCost, BigDecimal averageCost,
                           BigDecimal netConsumedCost, BigDecimal turns,
                           boolean available) {}

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public Turnover report(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        if(from==null || to==null || !from.isBefore(to) || ChronoUnit.DAYS.between(from,to)>366)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid inventory turnover period");
        var start=java.sql.Timestamp.from(from.atStartOfDay(ZONE).toInstant());
        var end=java.sql.Timestamp.from(to.atStartOfDay(ZONE).toInstant());
        var values=jdbc.sql("""
            SELECT
                COALESCE(SUM(CASE WHEN sm.occurred_at<:start
                     THEN CASE WHEN cm.direction='IN' THEN cm.amount ELSE -cm.amount END
                     ELSE 0 END),0) AS opening,
                COALESCE(SUM(CASE WHEN sm.occurred_at<:end
                     THEN CASE WHEN cm.direction='IN' THEN cm.amount ELSE -cm.amount END
                     ELSE 0 END),0) AS closing,
                COALESCE(SUM(CASE WHEN sm.occurred_at>=:start AND sm.occurred_at<:end
                     THEN CASE WHEN sm.movement_type='SALE_OUT' AND cm.direction='OUT' THEN cm.amount
                               WHEN sm.movement_type='RETURN_IN' AND cm.direction='IN' THEN -cm.amount
                               ELSE 0 END ELSE 0 END),0) AS consumed
            FROM inventory_cost_movements cm
            JOIN stock_movements sm ON sm.id=cm.stock_movement_id
            """).param("start",start).param("end",end)
            .query((rs,n)->new BigDecimal[]{rs.getBigDecimal("opening"),
                                            rs.getBigDecimal("closing"),rs.getBigDecimal("consumed")}).single();
        BigDecimal average=values[0].add(values[1]).divide(BigDecimal.valueOf(2),2,RoundingMode.HALF_UP);
        boolean available=average.signum()>0 && values[2].signum()>=0
                && values[0].signum()>=0 && values[1].signum()>=0;
        BigDecimal turns=available?values[2].divide(average,4,RoundingMode.HALF_UP):null;
        return new Turnover(from,to,values[0],values[1],average,values[2],turns,available);
    }
}
