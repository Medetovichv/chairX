package kg.chairx.dashboard.application;

import kg.chairx.dashboard.api.DashboardOverview;
import kg.chairx.sale.api.SaleSummary;
import kg.chairx.sale.persistence.SaleRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Bishkek");
    private final JdbcClient jdbc;
    private final SaleRepository sales;

    public DashboardService(JdbcClient jdbc, SaleRepository sales) {
        this.jdbc = jdbc;
        this.sales = sales;
    }

    public DashboardOverview overview() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        boolean canSales = allowed("SALES_READ");
        boolean canDeliveries = allowed("DELIVERIES_READ");
        boolean canInventory = allowed("INVENTORY_READ");

        // Missing grants are NOT encoded as zero, since that would lie to
        // the employee about the underlying business.
        Long orders = canSales ? count("""
                SELECT count(*) FROM sales
                WHERE created_at >= :fromDate
                """, today.minusDays(29)) : null;
        Long inWork = canSales ? jdbc.sql("""
                SELECT count(*) FROM sales
                WHERE status IN ('DRAFT','CONFIRMED')
                """).query(Long.class).single() : null;
        Long pending = canDeliveries ? jdbc.sql("""
                SELECT count(*) FROM deliveries
                WHERE status IN ('READY','IN_TRANSIT')
                """).query(Long.class).single() : null;
        Long late = canDeliveries ? jdbc.sql("""
                SELECT count(*) FROM deliveries
                WHERE status IN ('READY','IN_TRANSIT')
                  AND planned_delivery_date < :today
                """).param("today", today).query(Long.class).single() : null;

        Long stock = canInventory ? jdbc.sql("""
                SELECT coalesce(sum(on_hand - reserved - blocked), 0)
                FROM inventory_balances
                """).query(Long.class).single() : null;
        // Explicit informational threshold only; no stock reorder rule.
        Long lowStock = canInventory ? jdbc.sql("""
                SELECT count(*) FROM (
                    SELECT product_variant_id
                    FROM inventory_balances
                    GROUP BY product_variant_id
                    HAVING sum(on_hand - reserved - blocked) BETWEEN 1 AND 5
                ) variants
                """).query(Long.class).single() : null;

        return new DashboardOverview(today, orders, inWork, pending, late,
                stock, lowStock,
                canSales ? sevenDaySales(today) : List.of(),
                canSales ? sales.listSummaries(null, null, null, null, 0, 5) : List.of());
    }

    private Long count(String query, LocalDate from) {
        return jdbc.sql(query)
                .param("fromDate", from.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime())
                .query(Long.class).single();
    }

    private List<DashboardOverview.SalesDay> sevenDaySales(LocalDate today) {
        LocalDate firstDay = today.minusDays(6);
        Map<LocalDate, Long> counts = jdbc.sql("""
                SELECT (created_at AT TIME ZONE 'Asia/Bishkek')::date AS business_date,
                       count(*) AS orders
                FROM sales
                WHERE created_at >= :startInstant
                  AND created_at < :endInstant
                GROUP BY business_date
                """)
                .param("startInstant", firstDay.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime())
                .param("endInstant", today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime())
                .query((rs, row) -> Map.entry(rs.getObject("business_date", LocalDate.class),
                        rs.getLong("orders")))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<DashboardOverview.SalesDay> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate day = firstDay.plusDays(i);
            days.add(new DashboardOverview.SalesDay(day, counts.getOrDefault(day, 0L)));
        }
        return List.copyOf(days);
    }

    private static boolean allowed(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> permission.equals(a.getAuthority()));
    }
}
