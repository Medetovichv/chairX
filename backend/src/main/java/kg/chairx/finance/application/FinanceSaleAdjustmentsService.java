package kg.chairx.finance.application;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Accounting-side adjustments. A physical return reverses the recognized
 * item sale value and restores its original FIFO cost on return date, even
 * when the original sale occurred in an earlier month. Its later cash refund
 * or exchange credit is a settlement, not a second revenue reduction.
 * A cash refund WITHOUT a return is a price concession only if the original
 * fulfilled sale had already been recognized at the refund time.
 */
@Service
@Transactional(readOnly=true)
public class FinanceSaleAdjustmentsService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Bishkek");
    private final JdbcClient jdbc;
    public FinanceSaleAdjustmentsService(JdbcClient jdbc){this.jdbc=jdbc;}

    public record Adjustments(BigDecimal returnedRevenue,BigDecimal restoredFifoCost,
            BigDecimal refundOnlyDiscounts,boolean completeReturnCost,
            boolean unrecognizedAdjustments,long returnItemCount) {}

    public Adjustments forPeriod(LocalDate from,LocalDate to){
        Timestamp begin=Timestamp.from(from.atStartOfDay(ZONE).toInstant());
        Timestamp end=Timestamp.from(to.atStartOfDay(ZONE).toInstant());
        var returns=jdbc.sql("""
            SELECT ri.id,ri.quantity,si.unit_sale_price,r.created_at,
                   COALESCE(restoration.quantity,0) AS restored_quantity,
                   COALESCE(restoration.cost,0) AS restored_cost,
                   s.status AS sale_status,
                   (CASE WHEN s.fulfillment_type='SELF_PICKUP'
                       THEN s.fulfilled_at ELSE d.delivered_at END) AS recognized_at,
                   s.fulfillment_type,d.status AS delivery_status
            FROM returns r
            JOIN return_items ri ON ri.return_id=r.id
            JOIN sale_items si ON si.id=ri.sale_item_id
            JOIN sales s ON s.id=r.sale_id
            LEFT JOIN deliveries d ON d.sale_id=s.id
            LEFT JOIN LATERAL (
              SELECT SUM(rest.quantity) AS quantity,SUM(rest.amount) AS cost
              FROM stock_movements sm
              JOIN inventory_cost_restorations rest ON rest.return_movement_id=sm.id
              WHERE sm.movement_type='RETURN_IN' AND sm.source_type='SALE_RETURN'
                AND sm.operation_id=ri.id
            ) restoration ON TRUE
            WHERE r.created_at>=:begin AND r.created_at<:end
            ORDER BY r.id,ri.id
            """).param("begin",begin).param("end",end)
            .query((rs,i)->new ReturnLine(
                 rs.getLong("quantity"),rs.getBigDecimal("unit_sale_price"),
                 rs.getLong("restored_quantity"),rs.getBigDecimal("restored_cost"),
                 rs.getString("sale_status"),rs.getString("fulfillment_type"),
                 rs.getString("delivery_status"),
                 rs.getTimestamp("recognized_at")==null?null:rs.getTimestamp("recognized_at").toInstant(),
                 rs.getTimestamp("created_at").toInstant())).list();
        BigDecimal negativeSales=BigDecimal.ZERO,restoredCost=BigDecimal.ZERO;
        boolean valued=true,unrecognized=false;
        for(var line:returns){
            if(!line.originalRecognized()) {unrecognized=true;continue;}
            negativeSales=negativeSales.add(line.price().multiply(BigDecimal.valueOf(line.quantity())));
            restoredCost=restoredCost.add(line.restoredCost());
            if(line.restoredQuantity()!=line.quantity()) valued=false;
        }
        var refunds=jdbc.sql("""
            SELECT rf.amount,rf.refunded_at,
                   s.status AS sale_status,s.fulfillment_type,d.status AS delivery_status,
                   (CASE WHEN s.fulfillment_type='SELF_PICKUP'
                      THEN s.fulfilled_at ELSE d.delivered_at END) AS recognized_at
            FROM refunds rf JOIN sales s ON s.id=rf.sale_id
            LEFT JOIN deliveries d ON d.sale_id=s.id
            WHERE rf.return_id IS NULL
              AND rf.refunded_at>=:begin AND rf.refunded_at<:end
            """).param("begin",begin).param("end",end)
            .query((rs,i)->new RefundLine(rs.getBigDecimal("amount"),
                  rs.getString("sale_status"),rs.getString("fulfillment_type"),
                  rs.getString("delivery_status"),
                  rs.getTimestamp("recognized_at")==null?null:rs.getTimestamp("recognized_at").toInstant(),
                  rs.getTimestamp("refunded_at").toInstant())).list();
        BigDecimal discounts=BigDecimal.ZERO;
        for(var line:refunds){
            if(!line.originalRecognized()){unrecognized=true;continue;}
            discounts=discounts.add(line.amount());
        }
        return new Adjustments(negativeSales,restoredCost,discounts,valued,
                unrecognized,returns.size());
    }
    private static boolean recognized(String status,String fulfillment,String delivery,
                java.time.Instant recognition,java.time.Instant event){
        return "FULFILLED".equals(status) && recognition!=null
                && !recognition.isAfter(event)
                && ("SELF_PICKUP".equals(fulfillment) || "DELIVERED".equals(delivery));
    }
    private record ReturnLine(long quantity,BigDecimal price,long restoredQuantity,
            BigDecimal restoredCost,String status,String fulfillment,String delivery,
            java.time.Instant recognizedAt,java.time.Instant eventAt){
        boolean originalRecognized(){
            return recognized(status,fulfillment,delivery,recognizedAt,eventAt);
        }
    }
    private record RefundLine(BigDecimal amount,String status,String fulfillment,
            String delivery,java.time.Instant recognizedAt,java.time.Instant eventAt){
        boolean originalRecognized(){
            return recognized(status,fulfillment,delivery,recognizedAt,eventAt);
        }
    }
}
