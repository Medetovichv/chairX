package kg.chairx;

import org.springframework.jdbc.core.JdbcTemplate;

public final class TestDatabaseCleaner {

    private TestDatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbc) {
        jdbc.execute("""
                TRUNCATE TABLE
                    inventory_cost_movements, inventory_cost_allocations, inventory_cost_layers, exchange_settlements,
                    exchanges,
                    refunds,
                    return_items,
                    returns,
                    payments,
                    deliveries,
                    sale_items,
                    sales,
                    stock_movements,
                    inventory_balances
                RESTART IDENTITY
                """);
    }
}
