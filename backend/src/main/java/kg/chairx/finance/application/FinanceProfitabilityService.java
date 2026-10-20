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
    private final FinanceSaleAdjustmentsService adjustmentsService;
    public FinanceProfitabilityService(JdbcClient jdbc,ExpenseAccrualService accruals,
                                        FinanceSaleAdjustmentsService adjustmentsService){
        this.jdbc=jdbc;this.accruals=accruals;this.adjustmentsService=adjustmentsService;
    }
    public record SaleCost(UUID saleId,String saleNumber,BigDecimal recognizedRevenue,
                           BigDecimal fifoCost,boolean completeCost) {}
    public record Profitability(LocalDate from,LocalDate to,BigDecimal grossRevenue,
                                BigDecimal verifiedFifoCost,BigDecimal recognizedAccruals,
                                BigDecimal otherOperatingExpenses,
                                BigDecimal returnedRevenue,BigDecimal refundOnlyDiscounts,
                                BigDecimal restoredFifoCost,BigDecimal netRevenue,
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
                      COALESCE(SUM(a.quantity),0)=si.quantity
                        AND COUNT(DISTINCT sm.id)=1 AS valued
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
              OR (s.fulfillment_type IN ('CITY_DELIVERY','REGION_DELIVERY') AND d.status='DELIVERED'))
            AND (CASE WHEN s.fulfillment_type='SELF_PICKUP'
                          THEN s.fulfilled_at ELSE d.delivered_at END)>=:begin
            AND (CASE WHEN s.fulfillment_type='SELF_PICKUP'
                          THEN s.fulfilled_at ELSE d.delivered_at END)<:end
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
        // Cash expenses without a linked F14 accrual are recognized once.
        // Linked settlements do not create a second P&L cost.
        BigDecimal nonAccrualExpenses=jdbc.sql("""
            SELECT COALESCE(SUM(e.amount),0)
            FROM expenses e
            WHERE e.expense_date>=:from AND e.expense_date<:to
              AND NOT EXISTS (SELECT 1 FROM expense_accrual_payments ap
                              WHERE ap.expense_id=e.id)
            """).param("from",from).param("to",to).query(BigDecimal.class).single();
        boolean complete=rows.stream().allMatch(SaleCost::completeCost);
        // Economic returns reverse the ORIGINAL sale on the date of physical return.
        // A later refund tied to the return is a payment only, not second revenue.
        var adjustments=adjustmentsService.forPeriod(from,to);
        BigDecimal netRevenue=revenue.subtract(adjustments.returnedRevenue())
                .subtract(adjustments.refundOnlyDiscounts());
        boolean allValued=complete && adjustments.completeReturnCost();
        // A return/refund made before fulfillment cannot safely adjust
        // recognized sales; do not publish a misleading final profit.
        boolean available=allValued && !adjustments.unrecognizedAdjustments();
        BigDecimal gross=allValued?netRevenue.subtract(fifo)
                .add(adjustments.restoredFifoCost()):null;
        BigDecimal operating=available?gross.subtract(expenses).subtract(nonAccrualExpenses):null;
        return new Profitability(from,to,revenue,fifo,expenses,nonAccrualExpenses,
                adjustments.returnedRevenue(),adjustments.refundOnlyDiscounts(),
                adjustments.restoredFifoCost(),netRevenue,allValued,
                available,gross,operating,rows,
                available?"Revenue and FIFO reflect historical returns, refunds and allocated expenses":
                "Financial result unavailable: incomplete FIFO restoration, missing cost allocations or adjustments before fulfillment");
    }
}
