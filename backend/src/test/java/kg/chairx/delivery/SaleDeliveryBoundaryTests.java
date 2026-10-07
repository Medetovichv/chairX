package kg.chairx.sale;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.sale.api.CreateSaleItemRequest;
import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.application.SaleRuleViolationException;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.sale.domain.SaleStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static kg.chairx.inventory.domain.StockMovementType.ADJUSTMENT_IN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class SaleDeliveryBoundaryTests {

    @Autowired
    SaleService sales;

    @Autowired
    InventoryService inventory;

    @Autowired
    JdbcTemplate jdbc;

    UUID home;
    UUID product;
    UUID variant;
    UUID customer;

    @BeforeEach
    void fixture() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "sale-delivery-boundary-test-user",
                        null,
                        List.of()
                )
        );

        assertTestDatabase();
        clearData();

        home = jdbc.queryForObject(
                "select id from warehouses where code='HOME'",
                UUID.class
        );

        product = UUID.randomUUID();
        variant = UUID.randomUUID();
        customer = UUID.randomUUID();

        jdbc.update("""
                insert into products(
                    id,
                    name,
                    active,
                    created_at,
                    updated_at
                )
                values (?, 'Sale delivery boundary fixture', true, now(), now())
                """,
                product
        );

        jdbc.update("""
                insert into product_variants(
                    id,
                    product_id,
                    name,
                    recommended_sale_price,
                    active,
                    created_at,
                    updated_at
                )
                values (?, ?, 'Black', 10000, true, now(), now())
                """,
                variant,
                product
        );

        jdbc.update("""
                insert into customers(
                    id,
                    full_name,
                    phone,
                    active,
                    created_at,
                    updated_at
                )
                values (?, 'Boundary Customer', '+996555000001', true, now(), now())
                """,
                customer
        );

        inventory.recordMovement(
                new RecordStockMovement(
                        UUID.randomUUID(),
                        home,
                        variant,
                        ADJUSTMENT_IN,
                        20,
                        "SALE_DELIVERY_BOUNDARY_FIXTURE",
                        UUID.randomUUID(),
                        "sale-delivery-boundary-test"
                )
        );
    }

    @AfterEach
    void cleanup() {
        assertTestDatabase();

        clearData();

        jdbc.update(
                "delete from customers where id=?",
                customer
        );

        jdbc.update(
                "delete from product_variants where id=?",
                variant
        );

        jdbc.update(
                "delete from products where id=?",
                product
        );

        SecurityContextHolder.clearContext();
    }

    @Test
    void cityDeliverySaleCannotBeCancelledDirectly() {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                3
        );

        assertThatThrownBy(
                () -> sales.cancel(sale.id())
        )
                .isInstanceOfSatisfying(
                        SaleRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo("DELIVERY_REQUIRED")
                );

        var stored = sales.get(sale.id());

        assertThat(stored.status())
                .isEqualTo(SaleStatus.CONFIRMED);

        var balance = inventory.getBalance(
                home,
                variant
        );

        assertThat(balance.onHand())
                .isEqualTo(20);

        assertThat(balance.reserved())
                .isEqualTo(3);

        assertThat(balance.available())
                .isEqualTo(17);

        assertThat(saleOutCount())
                .isZero();
    }

    @Test
    void regionDeliverySaleCannotBeCancelledDirectly() {
        var sale = createSale(
                FulfillmentType.REGION_DELIVERY,
                2
        );

        assertThatThrownBy(
                () -> sales.cancel(sale.id())
        )
                .isInstanceOfSatisfying(
                        SaleRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo("DELIVERY_REQUIRED")
                );

        var stored = sales.get(sale.id());

        assertThat(stored.status())
                .isEqualTo(SaleStatus.CONFIRMED);

        var balance = inventory.getBalance(
                home,
                variant
        );

        assertThat(balance.onHand())
                .isEqualTo(20);

        assertThat(balance.reserved())
                .isEqualTo(2);

        assertThat(balance.available())
                .isEqualTo(18);

        assertThat(saleOutCount())
                .isZero();
    }

    private kg.chairx.sale.api.SaleResponse createSale(
            FulfillmentType fulfillmentType,
            long quantity
    ) {
        return sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customer,
                        fulfillmentType,
                        List.of(
                                new CreateSaleItemRequest(
                                        variant,
                                        home,
                                        quantity,
                                        new BigDecimal("8500")
                                )
                        )
                )
        );
    }

    private long saleOutCount() {
        return jdbc.queryForObject(
                """
                select count(*)
                from stock_movements
                where movement_type='SALE_OUT'
                """,
                Long.class
        );
    }

    private void clearData() {
        assertTestDatabase();

        jdbc.execute("""
                truncate table
                    payments,
                    deliveries,
                    sale_items,
                    sales,
                    stock_movements,
                    inventory_balances
                restart identity
                """);

        jdbc.execute(
                "alter sequence sale_number_seq restart with 1"
        );

        jdbc.update("""
                delete from audit_entries
                where entity_type in (
                    'SALE',
                    'DELIVERY',
                    'STOCK_MOVEMENT'
                )
                """);
    }

    private void assertTestDatabase() {
        String database = jdbc.queryForObject(
                "select current_database()",
                String.class
        );

        assertThat(database)
                .isEqualTo("chairx_test");
    }
}