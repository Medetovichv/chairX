package kg.chairx.finance.web;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Purchase obligations are based on the agreed ordered supplier costs and
 * cargo estimates, not on cash movement totals or FIFO stock valuations.
 */
@RestController
@RequestMapping("/api/finance/purchase-obligations")
public class FinancePurchaseObligationsController {
    private final JdbcClient jdbc;
    public FinancePurchaseObligationsController(JdbcClient jdbc){this.jdbc=jdbc;}

    public record PurchaseDebt(UUID purchaseId,String supplierName,java.time.Instant createdAt,String status,BigDecimal supplierTotal,
            BigDecimal cargoTotal,BigDecimal supplierPaid,BigDecimal cargoPaid,
            BigDecimal outstanding,BigDecimal prepaid) {}
    public record Obligations(BigDecimal totalOutstanding,BigDecimal totalPrepaid,
                              List<PurchaseDebt> purchases) {}

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public Obligations list() {
        var lines=jdbc.sql("""
          SELECT p.id,p.status,p.created_at,supplier.name AS supplier_name,
            COALESCE(items.amount,0) AS supplier_total,
            COALESCE(p.cargo_cost,0) AS cargo_total,
            COALESCE(pay.supplier_paid,0) AS supplier_paid,
            COALESCE(pay.cargo_paid,0) AS cargo_paid
          FROM purchases p
          JOIN suppliers supplier ON supplier.id=p.supplier_id
          LEFT JOIN LATERAL (
             SELECT SUM(ordered_quantity*purchase_unit_cost) AS amount
             FROM purchase_items WHERE purchase_id=p.id
          ) items ON TRUE
          LEFT JOIN LATERAL (
             SELECT SUM(amount) FILTER (WHERE payment_kind='SUPPLIER') AS supplier_paid,
                    SUM(amount) FILTER (WHERE payment_kind='CARGO') AS cargo_paid
             FROM purchase_payments WHERE purchase_id=p.id
          ) pay ON TRUE
          WHERE p.status IN ('CONFIRMED','PARTIALLY_RECEIVED','RECEIVED')
          ORDER BY p.created_at DESC,p.id DESC
          """).query((rs,i)->{
            BigDecimal supplier=rs.getBigDecimal("supplier_total");
            BigDecimal cargo=rs.getBigDecimal("cargo_total");
            BigDecimal paidSupplier=rs.getBigDecimal("supplier_paid");
            BigDecimal paidCargo=rs.getBigDecimal("cargo_paid");
            BigDecimal due=supplier.subtract(paidSupplier).max(BigDecimal.ZERO)
                    .add(cargo.subtract(paidCargo).max(BigDecimal.ZERO));
            BigDecimal advance=paidSupplier.subtract(supplier).max(BigDecimal.ZERO)
                    .add(paidCargo.subtract(cargo).max(BigDecimal.ZERO));
            return new PurchaseDebt(rs.getObject("id",UUID.class),rs.getString("supplier_name"),rs.getTimestamp("created_at").toInstant(),rs.getString("status"),
                  supplier,cargo,paidSupplier,paidCargo,
                  due,advance);
          }).list();
        return new Obligations(lines.stream().map(PurchaseDebt::outstanding)
                .reduce(BigDecimal.ZERO,BigDecimal::add),
                lines.stream().map(PurchaseDebt::prepaid)
                .reduce(BigDecimal.ZERO,BigDecimal::add),lines);
    }
}
