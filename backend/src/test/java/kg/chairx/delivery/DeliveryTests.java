package kg.chairx.delivery;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.delivery.api.CreateDeliveryRequest;
import kg.chairx.delivery.api.FailDeliveryRequest;
import kg.chairx.delivery.api.ReturnDeliveryToWarehouseRequest;
import kg.chairx.delivery.application.DeliveryRuleViolationException;
import kg.chairx.delivery.application.DeliveryService;
import kg.chairx.delivery.domain.DeliveryStatus;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.sale.api.CreateSaleItemRequest;
import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.sale.domain.SaleStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
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
class DeliveryTests {

    @Autowired
    DeliveryService deliveries;

    @Autowired
    SaleService sales;

    @Autowired
    InventoryService inventory;

    @Autowired
    JdbcTemplate jdbc;

    UUID home;
    UUID office;

    UUID product;
    UUID firstVariant;
    UUID secondVariant;

    UUID customer;

    @BeforeEach
    void fixture() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "delivery-test-user",
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

        office = jdbc.queryForObject(
                "select id from warehouses where code='OFFICE'",
                UUID.class
        );

        product = UUID.randomUUID();
        firstVariant = UUID.randomUUID();
        secondVariant = UUID.randomUUID();
        customer = UUID.randomUUID();

        jdbc.update("""
                insert into products(
                    id,
                    name,
                    active,
                    created_at,
                    updated_at
                )
                values (?, 'Delivery fixture', true, now(), now())
                """, product);

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
                """, firstVariant, product);

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
                values (?, ?, 'White', 12000, true, now(), now())
                """, secondVariant, product);

        jdbc.update("""
                insert into customers(
                    id,
                    full_name,
                    phone,
                    active,
                    created_at,
                    updated_at
                )
                values (?, 'Delivery Customer', '+996555000000', true, now(), now())
                """, customer);

        addStock(home, firstVariant, 20);
        addStock(office, secondVariant, 20);
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
                "delete from product_variants where id in (?, ?)",
                firstVariant,
                secondVariant
        );

        jdbc.update(
                "delete from products where id=?",
                product
        );

        SecurityContextHolder.clearContext();
    }

    @Test
    void createsReadyDeliveryForCityDeliverySale() {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 2, "8500")
        );

        var delivery = deliveries.create(
                deliveryRequest(sale.id())
        );

        assertThat(delivery.saleId())
                .isEqualTo(sale.id());

        assertThat(delivery.status())
                .isEqualTo(DeliveryStatus.READY);

        assertThat(delivery.recipientName())
                .isEqualTo("Иван Иванов");

        assertThat(delivery.recipientPhone())
                .isEqualTo("+996555111222");

        assertThat(delivery.address())
                .isEqualTo("Бишкек, ул. Тестовая 10");

        assertThat(delivery.deliveryCost())
                .isEqualByComparingTo("300");

        assertThat(delivery.createdBy())
                .isEqualTo("delivery-test-user");

        assertThat(count("deliveries"))
                .isEqualTo(1);

        var balance = inventory.getBalance(
                home,
                firstVariant
        );

        assertThat(balance.onHand())
                .isEqualTo(20);

        assertThat(balance.reserved())
                .isEqualTo(2);

        assertThat(saleOutCount())
                .isZero();
    }

    @Test
    void rejectsDeliveryForSelfPickupSale() {
        var sale = createSale(
                FulfillmentType.SELF_PICKUP,
                item(firstVariant, home, 1, "8500")
        );

        assertThatThrownBy(
                () -> deliveries.create(
                        deliveryRequest(sale.id())
                )
        )
                .isInstanceOf(DeliveryRuleViolationException.class)
                .satisfies(exception -> {
                    var rule =
                            (DeliveryRuleViolationException) exception;

                    assertThat(rule.code())
                            .isEqualTo("DELIVERY_NOT_ALLOWED");
                });

        assertThat(count("deliveries"))
                .isZero();
    }

    @Test
    void rejectsSecondDeliveryForSameSale() {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 1, "8500")
        );

        deliveries.create(
                deliveryRequest(sale.id())
        );

        assertThatThrownBy(
                () -> deliveries.create(
                        deliveryRequest(sale.id())
                )
        )
                .isInstanceOf(DeliveryRuleViolationException.class)
                .satisfies(exception -> {
                    var rule =
                            (DeliveryRuleViolationException) exception;

                    assertThat(rule.code())
                            .isEqualTo("DELIVERY_ALREADY_EXISTS");
                });

        assertThat(count("deliveries"))
                .isEqualTo(1);
    }

    @Test
    void dispatchFulfillsSaleAndPhysicallyRemovesStock() {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 3, "8500")
        );

        var delivery = deliveries.create(
                deliveryRequest(sale.id())
        );

        var dispatched = deliveries.dispatch(
                delivery.id()
        );

        assertThat(dispatched.status())
                .isEqualTo(DeliveryStatus.IN_TRANSIT);

        assertThat(dispatched.dispatchedBy())
                .isEqualTo("delivery-test-user");

        assertThat(dispatched.dispatchedAt())
                .isNotNull();

        var storedSale = sales.get(sale.id());

        assertThat(storedSale.status())
                .isEqualTo(SaleStatus.FULFILLED);

        var balance = inventory.getBalance(
                home,
                firstVariant
        );

        assertThat(balance.onHand())
                .isEqualTo(17);

        assertThat(balance.reserved())
                .isZero();

        assertThat(balance.available())
                .isEqualTo(17);

        assertThat(saleOutCount())
                .isEqualTo(1);
    }

    @Test
    void repeatedDispatchDoesNotCreateSecondSaleOut() {
        var sale = createSale(
                FulfillmentType.REGION_DELIVERY,
                item(firstVariant, home, 2, "8500")
        );

        var delivery = deliveries.create(
                deliveryRequest(sale.id())
        );

        deliveries.dispatch(delivery.id());

        long movementsAfterFirstDispatch =
                stockMovementCount();

        var repeated = deliveries.dispatch(
                delivery.id()
        );

        assertThat(repeated.status())
                .isEqualTo(DeliveryStatus.IN_TRANSIT);

        assertThat(stockMovementCount())
                .isEqualTo(movementsAfterFirstDispatch);

        assertThat(saleOutCount())
                .isEqualTo(1);

        var balance = inventory.getBalance(
                home,
                firstVariant
        );

        assertThat(balance.onHand())
                .isEqualTo(18);

        assertThat(balance.reserved())
                .isZero();
    }

    @Test
    void cancellingReadyDeliveryCancelsSaleAndReleasesReservation() {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 4, "8500")
        );

        var delivery = deliveries.create(
                deliveryRequest(sale.id())
        );

        var cancelled = deliveries.cancel(
                delivery.id()
        );

        assertThat(cancelled.status())
                .isEqualTo(DeliveryStatus.CANCELLED);

        assertThat(cancelled.cancelledBy())
                .isEqualTo("delivery-test-user");

        assertThat(cancelled.cancelledAt())
                .isNotNull();

        assertThat(
                sales.get(sale.id()).status()
        ).isEqualTo(SaleStatus.CANCELLED);

        var balance = inventory.getBalance(
                home,
                firstVariant
        );

        assertThat(balance.onHand())
                .isEqualTo(20);

        assertThat(balance.reserved())
                .isZero();

        assertThat(balance.available())
                .isEqualTo(20);

        assertThat(saleOutCount())
                .isZero();
    }

    @Test
    void deliveredDeliveryDoesNotChangeStockAgain() {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 2, "8500")
        );

        var delivery = deliveries.create(
                deliveryRequest(sale.id())
        );

        deliveries.dispatch(delivery.id());

        long movementsBeforeDelivered =
                stockMovementCount();

        var delivered = deliveries.markDelivered(
                delivery.id()
        );

        assertThat(delivered.status())
                .isEqualTo(DeliveryStatus.DELIVERED);

        assertThat(delivered.deliveredBy())
                .isEqualTo("delivery-test-user");

        assertThat(delivered.deliveredAt())
                .isNotNull();

        assertThat(stockMovementCount())
                .isEqualTo(movementsBeforeDelivered);

        var balance = inventory.getBalance(
                home,
                firstVariant
        );

        assertThat(balance.onHand())
                .isEqualTo(18);

        assertThat(balance.reserved())
                .isZero();
    }

    @Test
    void failedDeliveryDoesNotAutomaticallyReturnStock() {
        var sale = createSale(
                FulfillmentType.REGION_DELIVERY,
                item(firstVariant, home, 2, "8500")
        );

        var delivery = deliveries.create(
                deliveryRequest(sale.id())
        );

        deliveries.dispatch(delivery.id());

        long movementsBeforeFailure =
                stockMovementCount();

        var failed = deliveries.markFailed(
                delivery.id(),
                new FailDeliveryRequest(
                        "Получатель не забрал товар"
                )
        );

        assertThat(failed.status())
                .isEqualTo(DeliveryStatus.FAILED);

        assertThat(failed.failedBy())
                .isEqualTo("delivery-test-user");

        assertThat(failed.failedAt())
                .isNotNull();

        assertThat(failed.failureReason())
                .isEqualTo(
                        "Получатель не забрал товар"
                );

        assertThat(failed.returnedToWarehouseAt())
                .isNull();

        assertThat(stockMovementCount())
                .isEqualTo(movementsBeforeFailure);

        assertThat(returnInCount())
                .isZero();

        var balance = inventory.getBalance(
                home,
                firstVariant
        );

        assertThat(balance.onHand())
                .isEqualTo(18);

        assertThat(balance.reserved())
                .isZero();
    }

    @Test
    void failedDeliveryCanBePhysicallyReturnedToWarehouse() {
        var sale = createSale(
                FulfillmentType.REGION_DELIVERY,
                item(firstVariant, home, 3, "8500")
        );

        var delivery = deliveries.create(
                deliveryRequest(sale.id())
        );

        deliveries.dispatch(delivery.id());

        deliveries.markFailed(
                delivery.id(),
                new FailDeliveryRequest(
                        "Клиент отказался"
                )
        );

        var returned =
                deliveries.returnToWarehouse(
                        delivery.id(),
                        new ReturnDeliveryToWarehouseRequest(
                                home
                        )
                );

        assertThat(returned.status())
                .isEqualTo(DeliveryStatus.FAILED);

        assertThat(returned.returnedToWarehouseBy())
                .isEqualTo("delivery-test-user");

        assertThat(returned.returnedToWarehouseAt())
                .isNotNull();

        assertThat(returned.returnWarehouseId())
                .isEqualTo(home);

        var balance = inventory.getBalance(
                home,
                firstVariant
        );

        assertThat(balance.onHand())
                .isEqualTo(20);

        assertThat(balance.reserved())
                .isZero();

        assertThat(returnInCount())
                .isEqualTo(1);

        assertThat(
                sales.get(sale.id()).status()
        ).isEqualTo(SaleStatus.FULFILLED);
    }

    @Test
    void failedDeliveryCanReturnAllItemsToOneSelectedWarehouse() {
        var sale = createSale(
                FulfillmentType.REGION_DELIVERY,
                item(firstVariant, home, 2, "8500"),
                item(secondVariant, office, 3, "9500")
        );

        var delivery = deliveries.create(
                deliveryRequest(sale.id())
        );

        deliveries.dispatch(delivery.id());

        deliveries.markFailed(
                delivery.id(),
                new FailDeliveryRequest(
                        "Груз вернулся от перевозчика"
                )
        );

        deliveries.returnToWarehouse(
                delivery.id(),
                new ReturnDeliveryToWarehouseRequest(
                        home
                )
        );

        var firstAtHome = inventory.getBalance(
                home,
                firstVariant
        );

        var secondAtOffice = inventory.getBalance(
                office,
                secondVariant
        );

        var secondAtHome = inventory.getBalance(
                home,
                secondVariant
        );

        assertThat(firstAtHome.onHand())
                .isEqualTo(20);

        assertThat(secondAtOffice.onHand())
                .isEqualTo(17);

        assertThat(secondAtHome.onHand())
                .isEqualTo(3);

        assertThat(returnInCount())
                .isEqualTo(2);
    }

    @Test
    void repeatedReturnToWarehouseDoesNotIncreaseStockTwice() {
        var sale = createSale(
                FulfillmentType.REGION_DELIVERY,
                item(firstVariant, home, 2, "8500")
        );

        var delivery = deliveries.create(
                deliveryRequest(sale.id())
        );

        deliveries.dispatch(delivery.id());

        deliveries.markFailed(
                delivery.id(),
                new FailDeliveryRequest(
                        "Возврат"
                )
        );

        deliveries.returnToWarehouse(
                delivery.id(),
                new ReturnDeliveryToWarehouseRequest(
                        home
                )
        );

        long movementsAfterFirstReturn =
                stockMovementCount();

        var repeated =
                deliveries.returnToWarehouse(
                        delivery.id(),
                        new ReturnDeliveryToWarehouseRequest(
                                home
                        )
                );

        assertThat(repeated.returnedToWarehouseAt())
                .isNotNull();

        assertThat(stockMovementCount())
                .isEqualTo(movementsAfterFirstReturn);

        assertThat(returnInCount())
                .isEqualTo(1);

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).onHand()
        ).isEqualTo(20);
    }

    @Test
    void rejectsReturnToInactiveWarehouse() {
        var sale = createSale(
                FulfillmentType.REGION_DELIVERY,
                item(firstVariant, home, 1, "8500")
        );

        var delivery = deliveries.create(
                deliveryRequest(sale.id())
        );

        deliveries.dispatch(delivery.id());

        deliveries.markFailed(
                delivery.id(),
                new FailDeliveryRequest(
                        "Возврат"
                )
        );

        jdbc.update(
                """
                update warehouses
                set active=false
                where id=?
                """,
                office
        );

        try {
            assertThatThrownBy(
                    () -> deliveries.returnToWarehouse(
                            delivery.id(),
                            new ReturnDeliveryToWarehouseRequest(
                                    office
                            )
                    )
            )
                    .isInstanceOf(
                            DeliveryRuleViolationException.class
                    )
                    .satisfies(exception -> {
                        var rule =
                                (DeliveryRuleViolationException) exception;

                        assertThat(rule.code())
                                .isEqualTo(
                                        "WAREHOUSE_INACTIVE"
                                );
                    });

            assertThat(returnInCount())
                    .isZero();

            assertThat(
                    deliveries.get(
                            delivery.id()
                    ).returnedToWarehouseAt()
            ).isNull();
        } finally {
            jdbc.update(
                    """
                    update warehouses
                    set active=true
                    where id=?
                    """,
                    office
            );
        }
    }

    @Test
    void cannotCancelDeliveryAfterDispatch() {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 1, "8500")
        );

        var delivery = deliveries.create(
                deliveryRequest(sale.id())
        );

        deliveries.dispatch(delivery.id());

        assertThatThrownBy(
                () -> deliveries.cancel(
                        delivery.id()
                )
        )
                .isInstanceOf(
                        DeliveryRuleViolationException.class
                )
                .satisfies(exception -> {
                    var rule =
                            (DeliveryRuleViolationException) exception;

                    assertThat(rule.code())
                            .isEqualTo(
                                    "INVALID_DELIVERY_STATUS"
                            );
                });

        assertThat(
                deliveries.get(
                        delivery.id()
                ).status()
        ).isEqualTo(DeliveryStatus.IN_TRANSIT);

        assertThat(
                sales.get(sale.id()).status()
        ).isEqualTo(SaleStatus.FULFILLED);
    }

    @Test
    void dispatchFailureRollsBackDeliverySaleReservationAndMovements() {
        var sale = createSale(
                FulfillmentType.REGION_DELIVERY,
                item(firstVariant, home, 2, "8500"),
                item(secondVariant, office, 2, "9500")
        );

        var delivery = deliveries.create(
                deliveryRequest(sale.id())
        );

        long movementsBefore =
                stockMovementCount();

        jdbc.execute("""
                create function test_reject_delivery_second_sale_out()
                returns trigger
                language plpgsql
                as $$
                begin
                    if NEW.movement_type = 'SALE_OUT'
                       and NEW.product_variant_id = '%s'::uuid
                    then
                        raise exception 'Simulated delivery SALE_OUT failure';
                    end if;

                    return NEW;
                end;
                $$
                """.formatted(secondVariant));

        jdbc.execute("""
                create trigger test_reject_delivery_second_sale_out
                before insert on stock_movements
                for each row
                execute function test_reject_delivery_second_sale_out()
                """);

        try {
            assertThatThrownBy(
                    () -> deliveries.dispatch(
                            delivery.id()
                    )
            ).isInstanceOf(
                    DataAccessException.class
            );

            var storedDelivery =
                    deliveries.get(delivery.id());

            var storedSale =
                    sales.get(sale.id());

            assertThat(storedDelivery.status())
                    .isEqualTo(DeliveryStatus.READY);

            assertThat(storedDelivery.dispatchedAt())
                    .isNull();

            assertThat(storedSale.status())
                    .isEqualTo(SaleStatus.CONFIRMED);

            var homeBalance =
                    inventory.getBalance(
                            home,
                            firstVariant
                    );

            var officeBalance =
                    inventory.getBalance(
                            office,
                            secondVariant
                    );

            assertThat(homeBalance.onHand())
                    .isEqualTo(20);

            assertThat(homeBalance.reserved())
                    .isEqualTo(2);

            assertThat(officeBalance.onHand())
                    .isEqualTo(20);

            assertThat(officeBalance.reserved())
                    .isEqualTo(2);

            assertThat(stockMovementCount())
                    .isEqualTo(movementsBefore);

            assertThat(saleOutCount())
                    .isZero();
        } finally {
            jdbc.execute("""
                    drop trigger test_reject_delivery_second_sale_out
                    on stock_movements
                    """);

            jdbc.execute("""
                    drop function test_reject_delivery_second_sale_out()
                    """);
        }
    }

    private CreateDeliveryRequest deliveryRequest(
            UUID saleId
    ) {
        return new CreateDeliveryRequest(
                saleId,
                "Иван Иванов",
                "+996555111222",
                "Бишкек, ул. Тестовая 10",
                "Бишкек",
                new BigDecimal("300"),
                "ChairX Courier",
                null,
                "Позвонить перед доставкой"
        );
    }

    private kg.chairx.sale.api.SaleResponse createSale(
            FulfillmentType fulfillmentType,
            CreateSaleItemRequest... items
    ) {
        return sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customer,
                        fulfillmentType,
                        List.of(items)
                )
        );
    }

    private CreateSaleItemRequest item(
            UUID variantId,
            UUID warehouseId,
            long quantity,
            String unitSalePrice
    ) {
        return new CreateSaleItemRequest(
                variantId,
                warehouseId,
                quantity,
                new BigDecimal(unitSalePrice)
        );
    }

    private void addStock(
            UUID warehouseId,
            UUID variantId,
            long quantity
    ) {
        inventory.recordMovement(
                new RecordStockMovement(
                        UUID.randomUUID(),
                        warehouseId,
                        variantId,
                        ADJUSTMENT_IN,
                        quantity,
                        "DELIVERY_TEST_FIXTURE",
                        UUID.randomUUID(),
                        "delivery-test"
                )
        );
    }

    private long count(
            String table
    ) {
        return jdbc.queryForObject(
                "select count(*) from " + table,
                Long.class
        );
    }

    private long stockMovementCount() {
        return count("stock_movements");
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

    private long returnInCount() {
        return jdbc.queryForObject(
                """
                select count(*)
                from stock_movements
                where movement_type='RETURN_IN'
                  and source_type='DELIVERY_RETURN'
                """,
                Long.class
        );
    }

    private void clearData() {
        assertTestDatabase();

        jdbc.execute("""
                truncate table
                    exchange_settlements, exchanges, refunds,
                    return_items, returns, payments,
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