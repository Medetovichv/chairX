package kg.chairx.delivery;

import kg.chairx.inventory.cost.InventoryAdjustmentService;
import kg.chairx.PostgresTestConfiguration;
import kg.chairx.delivery.api.CreateDeliveryRequest;
import kg.chairx.delivery.api.FailDeliveryRequest;
import kg.chairx.delivery.api.ReturnDeliveryToWarehouseRequest;
import kg.chairx.delivery.application.DeliveryRuleViolationException;
import kg.chairx.delivery.application.DeliveryService;
import kg.chairx.delivery.domain.DeliveryStatus;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.application.PaymentRuleViolationException;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.returning.application.ReturnService;
import kg.chairx.returning.application.ReturnRuleViolationException;
import kg.chairx.returning.api.CreateReturnRequest;
import kg.chairx.returning.api.CreateReturnItemRequest;
import kg.chairx.returning.domain.ReturnCondition;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static kg.chairx.inventory.domain.StockMovementType.ADJUSTMENT_IN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.junit.jupiter.api.extension.ExtendWith(kg.chairx.FundedFinanceExtension.class)
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class DeliveryTests {

    @Autowired
    DeliveryService deliveries;

    @Autowired
    PaymentService payments;

    @Autowired
    ReturnService returns;

    @Autowired
    SaleService sales;

    @Autowired
    InventoryService inventory;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    org.springframework.jdbc.core.simple.JdbcClient jdbcClient;

    @Autowired
    InventoryAdjustmentService adjustments;

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
    void unnamedRecipientAndPlannedDateCanBeChangedBeforeCompletion() {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 1, "8500")
        );
        var base = deliveryRequest(sale.id());
        var date = LocalDate.of(2026, 10, 15);
        var created = deliveries.create(new CreateDeliveryRequest(
                sale.id(), null, base.recipientPhone(), base.address(),
                base.cityRegion(), base.deliveryCost(), base.carrierName(),
                base.trackingNumber(), base.comment(), date
        ));
        assertThat(created.recipientName()).isNull();
        assertThat(created.plannedDeliveryDate()).isEqualTo(date);
        assertThat(deliveries.get(created.id()).plannedDeliveryDate()).isEqualTo(date);

        var moved = date.plusDays(2);
        var rescheduled = deliveries.changePlannedDate(created.id(), moved);
        assertThat(rescheduled.plannedDeliveryDate()).isEqualTo(moved);
        assertThat(deliveries.list(null, null, null, moved, moved, 0, 20)
                .items()).hasSize(1);
        assertThat(deliveries.list(null, null, null, date, date, 0, 20)
                .items()).isEmpty();
        assertThat(deliveries.changePlannedDate(created.id(), moved))
                .isEqualTo(rescheduled);

        deliveries.dispatch(created.id());
        deliveries.markDelivered(created.id());
        assertThatThrownBy(() -> deliveries.changePlannedDate(created.id(), date))
                .isInstanceOf(DeliveryRuleViolationException.class);
    }

    @Test
    void inTransitIsNotACompletedSaleForDailyReport() {
        var sale = createSale(FulfillmentType.REGION_DELIVERY,
                item(firstVariant, home, 1, "8500"));
        var delivery = deliveries.create(deliveryRequest(sale.id()));
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        assertThat(kg.chairx.finance.application.DailyClosingSalesSnapshot.live(jdbcClient,date))
                .isEmpty();
        deliveries.dispatch(delivery.id());
        assertThat(sales.get(sale.id()).status()).isEqualTo(SaleStatus.FULFILLED);
        assertThat(kg.chairx.finance.application.DailyClosingSalesSnapshot.live(jdbcClient,date))
                .isEmpty();
        deliveries.markDelivered(delivery.id());
        var results = kg.chairx.finance.application.DailyClosingSalesSnapshot.live(jdbcClient,date);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().saleId()).isEqualTo(sale.id());
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



    @Test
    void readyDeliveryCancellationCannotBypassPaidSaleGuard() {
        var sale = createSale(FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 2, "8500"));
        var delivery = deliveries.create(deliveryRequest(sale.id()));
        var payment = payments.create(new CreatePaymentRequest(
                sale.id(), PaymentMethod.CASH, null, null));
        long beforeFinance = jdbc.queryForObject(
                "select count(*) from finance_movements", Long.class);

        assertThatThrownBy(() -> deliveries.cancel(delivery.id()))
                .isInstanceOfSatisfying(
                        kg.chairx.sale.application.SaleRuleViolationException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SALE_HAS_ACTIVE_PAYMENT"));

        assertThat(deliveries.get(delivery.id()).status()).isEqualTo(DeliveryStatus.READY);
        assertThat(sales.get(sale.id()).status()).isEqualTo(SaleStatus.CONFIRMED);
        assertThat(payments.get(payment.id()).status())
                .isEqualTo(kg.chairx.payment.domain.PaymentStatus.PAID);
        assertThat(inventory.getBalance(home, firstVariant).reserved()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from finance_movements", Long.class))
                .isEqualTo(beforeFinance);
    }


    @Test
    void successfullyDeliveredGoodsCanUseRegularPartialReturn() {
        var sale = createSale(FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 2, "8500"));
        var delivery = deliveries.create(deliveryRequest(sale.id()));
        deliveries.dispatch(delivery.id());
        deliveries.markDelivered(delivery.id());

        var request = new CreateReturnRequest(
                sale.id(), home, UUID.randomUUID(),
                List.of(new CreateReturnItemRequest(
                        sale.items().getFirst().id(), 1, ReturnCondition.SELLABLE)),
                "Обычный частичный возврат", null);
        var returned = returns.create(request);

        assertThat(returned.items()).hasSize(1);
        // Replaying an already successful Return is allowed and must not
        // post another receipt or cost restoration.
        long movementsBeforeReplay = stockMovementCount();
        long restorationsBeforeReplay = count("inventory_cost_restorations");
        assertThat(returns.create(request)).isEqualTo(returned);
        assertThat(stockMovementCount()).isEqualTo(movementsBeforeReplay);
        assertThat(count("inventory_cost_restorations")).isEqualTo(restorationsBeforeReplay);
        // The existing returnInCount() helper only counts DELIVERY_RETURN,
        // while regular customer returns are recorded as SALE_RETURN.
        assertThat(jdbc.queryForObject(
                "select count(*) from stock_movements "
                        + "where movement_type='RETURN_IN' and source_type='SALE_RETURN'",
                Long.class)).isEqualTo(1);
        assertThat(inventory.getBalance(home, firstVariant).onHand()).isEqualTo(19);
        assertThat(deliveries.get(delivery.id()).status()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void failedDeliveryCannotBeReturnedAgainViaRegularReturn() {
        var sale = createSale(FulfillmentType.REGION_DELIVERY,
                item(firstVariant, home, 2, "8500"));
        var delivery = deliveries.create(deliveryRequest(sale.id()));
        deliveries.dispatch(delivery.id());
        deliveries.markFailed(delivery.id(), new FailDeliveryRequest("Получатель отказался"));

        var attempt = new CreateReturnRequest(
                sale.id(), home, UUID.randomUUID(),
                List.of(new CreateReturnItemRequest(
                        sale.items().getFirst().id(), 2, ReturnCondition.SELLABLE)),
                "Неуспешная доставка", null);

        // FAILED alone does not restore inventory. The customer Return
        // workflow must not pretend that the shipment is back in stock.
        assertThat(returnInCount()).isZero();
        assertThat(inventory.getBalance(home, firstVariant).onHand()).isEqualTo(18);
        assertRegularReturnRejectedWithoutSideEffects(
                attempt, "DELIVERY_RETURN_WORKFLOW_REQUIRED");

        deliveries.returnToWarehouse(
                delivery.id(), new ReturnDeliveryToWarehouseRequest(home));
        long onHand = inventory.getBalance(home, firstVariant).onHand();
        // The dedicated warehouse receipt has now fully restored the goods;
        // a subsequent customer Return must still be rejected.
        assertRegularReturnRejectedWithoutSideEffects(
                attempt, "DELIVERY_RETURN_WORKFLOW_REQUIRED");
        assertThat(returnInCount()).isEqualTo(1);
        assertThat(inventory.getBalance(home, firstVariant).onHand()).isEqualTo(onHand);
        assertThat(jdbc.queryForObject("select count(*) from returns", Long.class)).isZero();
    }

    @Test
    void failedAndPhysicallyReturnedDeliveryCannotReceiveNewPayment() {
        var sale = createSale(FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 1, "8500"));
        var delivery = deliveries.create(deliveryRequest(sale.id()));
        deliveries.dispatch(delivery.id());
        deliveries.markFailed(delivery.id(), new FailDeliveryRequest("Не доставлено"));
        deliveries.returnToWarehouse(
                delivery.id(), new ReturnDeliveryToWarehouseRequest(home));

        assertThatThrownBy(() -> payments.create(new CreatePaymentRequest(
                sale.id(), PaymentMethod.CASH, null, null)))
                .isInstanceOfSatisfying(PaymentRuleViolationException.class,
                        error -> assertThat(error.getCode()).isEqualTo(
                                "FAILED_DELIVERY_ALREADY_RETURNED"));

        assertThat(jdbc.queryForObject(
                "select count(*) from payments where sale_id=?", Long.class, sale.id()))
                .isZero();
        assertThat(inventory.getBalance(home, firstVariant).onHand()).isEqualTo(20);
    }

    @Test
    void selfPickupCanUseRegularReturnAfterFulfillment() {
        var sale = createSale(FulfillmentType.SELF_PICKUP,
                item(firstVariant, home, 1, "8500"));
        sales.fulfill(sale.id());
        var result = returns.create(regularReturn(sale.id(), sale.items().getFirst().id(), 1));
        assertThat(result.items()).hasSize(1);
        assertThat(inventory.getBalance(home, firstVariant).onHand()).isEqualTo(20);
    }

    @Test
    void cityDeliveryWithoutDeliveryCannotCreateCustomerReturn() {
        var sale = createSale(FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 1, "8500"));
        assertRegularReturnRejectedWithoutSideEffects(
                regularReturn(sale.id(), sale.items().getFirst().id(), 1),
                "DELIVERY_NOT_COMPLETED");
    }

    @Test
    void cityDeliveryReadyCannotCreateCustomerReturn() {
        var sale = createSale(FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 1, "8500"));
        deliveries.create(deliveryRequest(sale.id()));
        assertRegularReturnRejectedWithoutSideEffects(
                regularReturn(sale.id(), sale.items().getFirst().id(), 1),
                "DELIVERY_NOT_COMPLETED");
    }

    @Test
    void cityDeliveryInTransitCannotCreateCustomerReturn() {
        assertNoCustomerReturnDuringTransit(FulfillmentType.CITY_DELIVERY);
    }

    @Test
    void regionDeliveryInTransitCannotCreateCustomerReturn() {
        assertNoCustomerReturnDuringTransit(FulfillmentType.REGION_DELIVERY);
    }

    private void assertNoCustomerReturnDuringTransit(FulfillmentType fulfillment) {
        var sale = createSale(fulfillment, item(firstVariant, home, 1, "8500"));
        var delivery = deliveries.create(deliveryRequest(sale.id()));
        deliveries.dispatch(delivery.id());
        assertThat(sales.get(sale.id()).status()).isEqualTo(SaleStatus.FULFILLED);
        assertThat(deliveries.get(delivery.id()).status()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertRegularReturnRejectedWithoutSideEffects(
                regularReturn(sale.id(), sale.items().getFirst().id(), 1),
                "DELIVERY_NOT_COMPLETED");
    }

    @Test
    void twoRegularPartialReturnsAfterDeliveryRestoreCostExactly() {
        var sale = createSale(FulfillmentType.REGION_DELIVERY,
                item(firstVariant, home, 3, "8500"));
        var delivery = deliveries.create(deliveryRequest(sale.id()));
        deliveries.dispatch(delivery.id());
        deliveries.markDelivered(delivery.id());

        var first = returns.create(regularReturn(sale.id(), sale.items().getFirst().id(), 1));
        assertThat(first.items()).hasSize(1);
        assertThat(inventory.getBalance(home, firstVariant).onHand()).isEqualTo(18);
        assertThat(jdbc.queryForObject(
                "select coalesce(sum(amount), 0) from inventory_cost_restorations",
                BigDecimal.class)).isEqualByComparingTo("5000");

        var second = returns.create(regularReturn(sale.id(), sale.items().getFirst().id(), 1));
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(inventory.getBalance(home, firstVariant).onHand()).isEqualTo(19);
        assertThat(count("return_items")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select coalesce(sum(amount), 0) from inventory_cost_restorations",
                BigDecimal.class)).isEqualByComparingTo("10000");
    }

    private CreateReturnRequest regularReturn(UUID saleId, UUID saleItemId, long quantity) {
        return new CreateReturnRequest(
                saleId, home, UUID.randomUUID(),
                List.of(new CreateReturnItemRequest(saleItemId, quantity, ReturnCondition.SELLABLE)),
                "Возврат клиента", null);
    }

    private void assertRegularReturnRejectedWithoutSideEffects(
            CreateReturnRequest request, String expectedCode) {
        long returnsBefore = count("returns");
        long itemsBefore = count("return_items");
        long movementsBefore = stockMovementCount();
        long restorationsBefore = count("inventory_cost_restorations");
        var balanceBefore = inventory.getBalance(home, firstVariant);

        assertThatThrownBy(() -> returns.create(request))
                .isInstanceOfSatisfying(ReturnRuleViolationException.class,
                        error -> assertThat(error.getCode()).isEqualTo(expectedCode));

        assertThat(count("returns")).isEqualTo(returnsBefore);
        assertThat(count("return_items")).isEqualTo(itemsBefore);
        assertThat(stockMovementCount()).isEqualTo(movementsBefore);
        assertThat(count("inventory_cost_restorations")).isEqualTo(restorationsBefore);
        assertThat(inventory.getBalance(home, firstVariant)).isEqualTo(balanceBefore);
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
        adjustments.recordValuedAdjustmentIn(
                UUID.randomUUID(),
                warehouseId,
                variantId,
                quantity,
                BigDecimal.valueOf(5000)
                        .multiply(BigDecimal.valueOf(quantity)),
                "delivery-test"
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
                    inventory_transfer_cost_origins, inventory_transfers, inventory_cost_allocations,
                    inventory_cost_movements,
                    inventory_cost_restorations, inventory_cost_write_offs, inventory_cost_layers, inventory_cost_movements, inventory_cost_allocations, inventory_cost_layers, exchange_settlements, exchanges, refunds,
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
                    'PAYMENT',
                    'RETURN',
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