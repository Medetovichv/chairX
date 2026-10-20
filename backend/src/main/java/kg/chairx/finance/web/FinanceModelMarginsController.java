package kg.chairx.finance.web;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Sale and return margins use original FIFO; direct refund discounts lack an item-level allocation.
 * The margin is null rather than misleading for any affected variation. */
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
           WITH sale_lines AS (
              SELECT si.product_variant_id AS variant_id,
                     si.quantity::bigint AS qty,
                     (si.quantity*si.unit_sale_price) AS recognized_value,
                     COALESCE(cost.allocated_cost,0) AS fifo_cost,
                     COALESCE(cost.allocated_qty,0)=si.quantity
                         AND COALESCE(cost.movement_count,0)=1 AS valued
              FROM sale_items si
              JOIN sales s ON s.id=si.sale_id
              LEFT JOIN deliveries d ON d.sale_id=s.id
              LEFT JOIN LATERAL (
                  SELECT SUM(a.allocated_cost) AS allocated_cost,
                         SUM(a.quantity) AS allocated_qty,
                         COUNT(DISTINCT sm.id) AS movement_count
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
           ), returned_lines AS (
              SELECT si.product_variant_id AS variant_id,
                     (-ri.quantity)::bigint AS qty,
                     -(ri.quantity*si.unit_sale_price) AS recognized_value,
                     -COALESCE(rest.cost,0) AS fifo_cost,
                     COALESCE(rest.qty,0)=ri.quantity
                         AND s.status='FULFILLED'
                         AND (s.fulfillment_type='SELF_PICKUP'
                              OR (s.fulfillment_type IN ('CITY_DELIVERY','REGION_DELIVERY')
                                  AND d.status='DELIVERED'))
                         AND (CASE WHEN s.fulfillment_type='SELF_PICKUP'
                                   THEN s.fulfilled_at ELSE d.delivered_at END)<=r.created_at AS valued
              FROM returns r
              JOIN return_items ri ON ri.return_id=r.id
              JOIN sale_items si ON si.id=ri.sale_item_id
              JOIN sales s ON s.id=r.sale_id
              LEFT JOIN deliveries d ON d.sale_id=s.id
              LEFT JOIN LATERAL (
                 SELECT SUM(cr.amount) AS cost,SUM(cr.quantity) AS qty
                 FROM stock_movements sm
                 JOIN inventory_cost_restorations cr ON cr.return_movement_id=sm.id
                 WHERE sm.operation_id=ri.id AND sm.source_type='SALE_RETURN'
                   AND sm.movement_type='RETURN_IN'
              ) rest ON TRUE
              WHERE r.created_at>=:from AND r.created_at<:to
           ), refund_lines AS (
              -- No item-level refund allocation exists. Preserve a row for the
              -- affected variation but explicitly make its margin unverified.
              SELECT DISTINCT si.product_variant_id AS variant_id,
                     0::bigint AS qty,0::numeric AS recognized_value,
                     0::numeric AS fifo_cost,FALSE AS valued
              FROM refunds rf
              JOIN sale_items si ON si.sale_id=rf.sale_id
              WHERE rf.return_id IS NULL
                AND rf.refunded_at>=:from AND rf.refunded_at<:to
           ), movements AS (
              SELECT * FROM sale_lines
              UNION ALL SELECT * FROM returned_lines
              UNION ALL SELECT * FROM refund_lines
           )
           SELECT p.id AS model_id,p.name AS model,v.id AS variant_id,v.name AS variation,
                  SUM(m.qty) AS units,
                  SUM(m.recognized_value) AS revenue,
                  SUM(m.fifo_cost) AS fifo_cost,
                  BOOL_AND(m.valued) AS cost_complete,
                  EXISTS (
                      SELECT 1 FROM refunds rf
                      JOIN sale_items affected ON affected.sale_id=rf.sale_id
                      WHERE affected.product_variant_id=v.id AND rf.return_id IS NULL
                        AND rf.refunded_at>=:from AND rf.refunded_at<:to
                  ) AS unattributed_discount
           FROM movements m
           JOIN product_variants v ON v.id=m.variant_id
           JOIN products p ON p.id=v.product_id
           GROUP BY p.id,p.name,v.id,v.name
           ORDER BY revenue DESC,model,variation
           """).param("from",java.sql.Timestamp.from(from.atStartOfDay(ZONE).toInstant()))
              .param("to",java.sql.Timestamp.from(to.atStartOfDay(ZONE).toInstant()))
              .query((rs,i)->{
                  BigDecimal revenue=rs.getBigDecimal("revenue");
                  BigDecimal cost=rs.getBigDecimal("fifo_cost");
                  boolean complete=rs.getBoolean("cost_complete")
                          && !rs.getBoolean("unattributed_discount");
                  return new Margin(rs.getObject("model_id",UUID.class),rs.getString("model"),
                     rs.getObject("variant_id",UUID.class),rs.getString("variation"),
                     rs.getLong("units"),revenue,cost,complete,
                     complete?revenue.subtract(cost):null);
              }).list();
    }
}
