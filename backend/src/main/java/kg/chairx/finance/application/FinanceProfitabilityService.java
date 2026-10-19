package kg.chairx.finance.application;

import kg.chairx.expense.application.ExpenseAccrualService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Read-only recognized sales and FIFO exposure, independent of cash postings.
 * Revenue recognition follows fulfillment independently of when cash arrives. Returns/exchanges
 * are deliberately excluded from net profit until compensation can be reconciled.
 */
@Service
@Transactional(readOnly=true)
public class FinanceProfitabilityService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Bishkek");
    private final JdbcClient jdbc;
    private final ExpenseAccrualService accruals;
    public FinanceProfitabilityService(JdbcClient jdbc,ExpenseAccrualService accruals){
        this.jdbc=jdbc;this.accruals=accruals;
    }
    public record SaleCost(UUID saleId,String saleNumber,BigDecimal recognizedRevenue,
                           BigDecimal fifoCost,boolean completeCost) {}
    public record Profitability(LocalDate from,LocalDate to,BigDecimal grossRevenue,
                                BigDecimal verifiedFifoCost,BigDecimal recognizedAccruals,
                                boolean completeCost,boolean netProfitAvailable,
                                BigDecimal grossProfit,BigDecimal operatingProfit,
                                List<SaleCost> sales,String caveat) {}
    public Profitability calculate(LocalDate from,LocalDate to){
        if(from==null||to==null||!from.isBefore(to)
                ||java.time.temporal.ChronoUnit.DAYS.between(from,to)>93)
            throw new IllegalArgumentException("Invalid profitability period");
        var begin=from.atStartOfDay(ZONE).toOffsetDateTime();
        var end=to.atStartOfDay(ZONE).toOffsetDateTime();
        List<SaleCost> rows=jdbc.sql("""
          SELECT s.id,s.sale_number,detail.total,COALESCE(cost.value,0) AS fifo_cost,
                 COALESCE(cost.valued_items,0)=detail.item_count AS complete_cost
          FROM sales s
          LEFT JOIN deliveries d ON d.sale_id=s.id
          JOIN LATERAL (
              SELECT SUM(si.quantity*si.unit_sale_price) AS total,
                     COUNT(*) AS item_count
              FROM sale_items si WHERE si.sale_id=s.id
              HAVING COUNT(*)>0
          ) detail ON TRUE
          LEFT JOIN LATERAL (
             SELECT COALESCE(SUM(per_item.cost),0) AS value,
                    COUNT(*) FILTER (WHERE per_item.valued) AS valued_items
             FROM (
               SELECT si.id,
                      COALESCE(SUM(a.allocated_cost),0) AS cost,
                      COUNT(a.id)>0 AS valued
               FROM sale_items si
               LEFT JOIN stock_movements sm
                 ON sm.source_type='SALE_ITEM' AND sm.source_id=si.id
                   AND sm.movement_type='SALE_OUT'
               LEFT JOIN inventory_cost_allocations a ON a.stock_movement_id=sm.id
               WHERE si.sale_id=s.id GROUP BY si.id
             ) per_item
          ) cost ON TRUE
          WHERE s.status='FULFILLED'
            AND (s.fulfillment_type='SELF_PICKUP'
              OR (s.fulfillment_type='CITY_DELIVERY' AND d.status IN ('IN_TRANSIT','DELIVERED'))
              OR (s.fulfillment_type='REGION_DELIVERY' AND d.status='DELIVERED'))
            AND (CASE WHEN s.fulfillment_type='REGION_DELIVERY'
                          THEN d.delivered_at ELSE s.fulfilled_at END)>=:begin
            AND (CASE WHEN s.fulfillment_type='REGION_DELIVERY'
                          THEN d.delivered_at ELSE s.fulfilled_at END)<:end
          ORDER BY s.id
          """).param("begin",begin).param("end",end)
          .query((rs,i)->new SaleCost(rs.getObject("id",UUID.class),
              rs.getString("sale_number"),rs.getBigDecimal("total"),
              rs.getBigDecimal("fifo_cost"),rs.getBoolean("complete_cost"))).list();
        BigDecimal revenue=rows.stream().map(SaleCost::recognizedRevenue)
                .reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal fifo=rows.stream().map(SaleCost::fifoCost)
                .reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal expenses=accruals.report(from,to).stream()
                .map(ExpenseAccrualService.ReportLine::recognized)
                .reduce(BigDecimal.ZERO,BigDecimal::add);
        boolean complete=rows.stream().allMatch(SaleCost::completeCost);
        // Returns, exchanges and ordinary non-accrual expenses require the
        // final accounting reconciliation; refuse to publish net profit.
        return new Profitability(from,to,revenue,fifo,expenses,complete,false,
                complete?revenue.subtract(fifo):null,null,rows,
                "Net P&L requires reconciliation of returns, exchanges and non-accrual operating expenses");
    }
}
