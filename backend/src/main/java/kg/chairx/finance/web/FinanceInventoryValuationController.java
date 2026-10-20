package kg.chairx.finance.web;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Current FIFO inventory valuation, not a historical period-end balance.
 * Financial costs are never served to INVENTORY_READ-only users.
 */
@RestController
@RequestMapping("/api/finance/inventory-valuation")
public class FinanceInventoryValuationController {
    private final JdbcClient jdbc;
    public FinanceInventoryValuationController(JdbcClient jdbc){this.jdbc=jdbc;}
    public record ModelValue(UUID productId,String model,long quantity,BigDecimal cost) {}
    public record Valuation(BigDecimal totalCost,long quantity,List<ModelValue> models) {}

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public Valuation current() {
        var models=jdbc.sql("""
            SELECT p.id,p.name,
                   COALESCE(SUM(l.quantity_remaining),0) AS units,
                   COALESCE(SUM(l.remaining_cost),0) AS cost
            FROM inventory_cost_layers l
            JOIN product_variants v ON v.id=l.product_variant_id
            JOIN products p ON p.id=v.product_id
            WHERE l.quantity_remaining>0
            GROUP BY p.id,p.name
            ORDER BY cost DESC,p.name
            """).query((rs,i)->new ModelValue(
                    rs.getObject("id",UUID.class),rs.getString("name"),
                    rs.getLong("units"),rs.getBigDecimal("cost"))).list();
        return new Valuation(models.stream().map(ModelValue::cost)
                .reduce(BigDecimal.ZERO,BigDecimal::add),
                models.stream().mapToLong(ModelValue::quantity).sum(),models);
    }
}
