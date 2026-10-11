package kg.chairx.dashboard.api;

import kg.chairx.sale.api.SaleSummary;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only, least-privilege dashboard projection. A null metric means the
 * employee lacks permission; zero means the employee is allowed and there
 * are no matching records. Neither cost, margin nor account balances leak.
 */
public record DashboardOverview(
        LocalDate businessDate,
        Long orders30Days,
        Long activeOrders,
        Long pendingDeliveries,
        Long delayedDeliveries,
        Long availableUnits,
        Long lowStockVariants,
        List<SalesDay> ordersByDay,
        List<SaleSummary> recentSales
) {
    public record SalesDay(LocalDate day, long orders) {}
}
