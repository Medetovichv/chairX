package kg.chairx.finance.web;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Margin by real sold chair model and variation, never estimated retail cost. */
@RestController
@RequestMapping("/api/finance/model-margins")
public class FinanceModelMarginsController {
    private static final ZoneId ZONE=ZoneId.of("Asia/Bishkek");
    private final JdbcClient jdbc;
    public FinanceModelMarginsController(JdbcClient jdbc){this.jdbc=jdbc;}
    public record Margin(UUID modelId,String model,UUID variantId,String variation,
                         long quantity,BigDecimal revenue,BigDecimal knownFifoCost,
                         boolean costComplete,BigDecimal grossProfit) {}
    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public List<Margin> list(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){
        if(from==null||to==null||!from.isBefore(to)
                ||java.time.temporal.ChronoUnit.DAYS.between(from,to)>93) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid margin period");
        }
        return jdbc.sql("""
           SELECT p.id AS model_id,p.name AS model,v.id AS variant_id,v.name AS variation,
                  SUM(si.quantity) AS units,
                  SUM(si.quantity*si.unit_sale_price) AS revenue,
                  SUM(COALESCE(cost.amount,0)) AS fifo_cost,
                  BOOL_AND(cost.valued) AS complete
           FROM sale_items si
           JOIN sales s ON s.id=si.sale_id
           JOIN product_variants v ON v.id=si.product_variant_id
           JOIN products p ON p.id=v.product_id
           LEFT JOIN deliveries d ON d.sale_id=s.id
           LEFT JOIN LATERAL (
             SELECT COALESCE(SUM(a.allocated_cost),0) AS amount,
                    COUNT(a.id)>0 AS valued
             FROM stock_movements sm
             LEFT JOIN inventory_cost_allocations a ON a.stock_movement_id=sm.id
             WHERE sm.source_type='SALE_ITEM' AND sm.source_id=si.id
               AND sm.movement_type='SALE_OUT'
           ) cost ON TRUE
           WHERE s.status='FULFILLED'
             AND (s.fulfillment_type='SELF_PICKUP'
                  OR (s.fulfillment_type IN ('CITY_DELIVERY','REGION_DELIVERY')
                      AND d.status='DELIVERED'))
             AND (CASE WHEN s.fulfillment_type='SELF_PICKUP'
                       THEN s.fulfilled_at ELSE d.delivered_at END)>=:from
             AND (CASE WHEN s.fulfillment_type='SELF_PICKUP'
                       THEN s.fulfilled_at ELSE d.delivered_at END)<:to
           GROUP BY p.id,p.name,v.id,v.name
           ORDER BY revenue DESC,model,variation
           """).param("from",java.sql.Timestamp.from(from.atStartOfDay(ZONE).toInstant()))
              .param("to",java.sql.Timestamp.from(to.atStartOfDay(ZONE).toInstant()))
              .query((rs,i)->{
                  BigDecimal revenue=rs.getBigDecimal("revenue");
                  BigDecimal cost=rs.getBigDecimal("fifo_cost");
                  boolean complete=rs.getBoolean("complete");
                  return new Margin(rs.getObject("model_id",UUID.class),rs.getString("model"),
                     rs.getObject("variant_id",UUID.class),rs.getString("variation"),
                     rs.getLong("units"),revenue,cost,complete,
                     complete?revenue.subtract(cost):null);
              }).list();
    }
}
