package kg.chairx.sale;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import kg.chairx.PostgresTestConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.domain.InsufficientStockException;
import kg.chairx.sale.api.CreateSaleItemRequest;
import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.api.CreateDraftSaleRequest;
import kg.chairx.sale.api.UpdateDraftSaleRequest;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import kg.chairx.inventory.cost.InventoryAdjustmentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
class SaleTests {

    @Autowired
    SaleService sales;

    @Autowired
    InventoryService inventory;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    InventoryAdjustmentService adjustments;

    @Autowired
    org.springframework.jdbc.core.simple.JdbcClient jdbcClient;

    @Autowired
    org.springframework.transaction.support.TransactionTemplate transactions;

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
                        "sale-test-user",
                        null,
                        List.of()
                )
        );

        assertTestDatabase();

        clearSaleData();

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
                values (?, 'Sale fixture', true, now(), now())
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
                values (?, 'Sale Customer', '+996555000000', true, now(), now())
                """, customer);

        addStock(home, firstVariant, 20);
        addStock(office, secondVariant, 20);
    }

    @AfterEach
    void cleanup() {
        assertTestDatabase();

        clearSaleData();

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
    void draftWithoutItemsDoesNotReserveStockAndCanBeCancelled() {
        long before = stockMovementCount();
        UUID key = UUID.randomUUID();
        var created = sales.createDraft(new CreateDraftSaleRequest(
                key, customer, null, List.of(), "Клиент думает"));
        assertThat(created.status()).isEqualTo(SaleStatus.DRAFT);
        assertThat(created.fulfillmentType()).isNull();
        assertThat(created.items()).isEmpty();
        assertThat(created.comment()).isEqualTo("Клиент думает");
        assertThat(sales.createDraft(new CreateDraftSaleRequest(
                key, customer, null, List.of(), "Клиент думает")).id()).isEqualTo(created.id());
        assertThat(inventory.getBalance(home, firstVariant).reserved()).isZero();
        assertThat(stockMovementCount()).isEqualTo(before);
        assertThat(count("payments")).isZero();
        assertThat(sales.list(0, 20, SaleStatus.DRAFT, null, null, null).items())
                .extracting(kg.chairx.sale.api.SaleSummary::id).contains(created.id());

        var cancelled = sales.cancel(created.id());
        assertThat(cancelled.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(inventory.getBalance(home, firstVariant).reserved()).isZero();
        assertThat(stockMovementCount()).isEqualTo(before);
        assertThatThrownBy(() -> sales.confirmDraft(created.id()))
                .isInstanceOf(SaleRuleViolationException.class);
    }

    @Test
    void simultaneousDraftConfirmationsReserveOnce() throws Exception {
        var draft = sales.createDraft(new CreateDraftSaleRequest(
                UUID.randomUUID(), customer, FulfillmentType.SELF_PICKUP,
                List.of(item(firstVariant, home, 3, "8500")), "Reserve once"));
        long beforeMovements = stockMovementCount();
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<kg.chairx.sale.api.SaleResponse>) () -> {
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                "p22-parallel-confirm", null, List.of()));
                try {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Draft confirmation workers not ready");
                    }
                    return sales.confirmDraft(draft.id());
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            Future<kg.chairx.sale.api.SaleResponse> first = pool.submit(task);
            Future<kg.chairx.sale.api.SaleResponse> second = pool.submit(task);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).status()).isEqualTo(SaleStatus.CONFIRMED);
            assertThat(second.get(15, TimeUnit.SECONDS).status()).isEqualTo(SaleStatus.CONFIRMED);
        }
        assertThat(inventory.getBalance(home, firstVariant).reserved()).isEqualTo(3);
        assertThat(count("sales")).isEqualTo(1);
        assertThat(stockMovementCount()).isEqualTo(beforeMovements);
        assertThat(saleAuditCount()).isEqualTo(2); // DRAFT_CREATED + CONFIRMED
    }

    @Test
    void draftCanBeEditedAndConfirmedAtomically() {
        var draft = sales.createDraft(new CreateDraftSaleRequest(
                UUID.randomUUID(), null, null, null, null));
        assertThat(draft.items()).isEmpty();
        assertThat(draft.customerId()).isNull();

        var edited = sales.updateDraft(draft.id(), new UpdateDraftSaleRequest(
                customer, FulfillmentType.SELF_PICKUP,
                List.of(item(firstVariant, home, 2, "8500")),
                "Покупатель подтвердил цену"));
        assertThat(edited.status()).isEqualTo(SaleStatus.DRAFT);
        assertThat(edited.items()).hasSize(1);
        assertThat(edited.comment()).isEqualTo("Покупатель подтвердил цену");
        assertThat(inventory.getBalance(home, firstVariant).reserved()).isZero();

        var confirmed = sales.confirmDraft(draft.id());
        assertThat(confirmed.status()).isEqualTo(SaleStatus.CONFIRMED);
        assertThat(inventory.getBalance(home, firstVariant).reserved()).isEqualTo(2);
        assertThat(sales.confirmDraft(draft.id()).id()).isEqualTo(confirmed.id());
        assertThat(inventory.getBalance(home, firstVariant).reserved()).isEqualTo(2);
        assertThatThrownBy(() -> sales.updateDraft(draft.id(),
                new UpdateDraftSaleRequest(customer, null, null, null)))
                .isInstanceOf(SaleRuleViolationException.class);
    }

    @Test
    void draftConfirmationFailureRollsBackAllReserves() {
        var draft = sales.createDraft(new CreateDraftSaleRequest(
                UUID.randomUUID(), customer, FulfillmentType.SELF_PICKUP,
                List.of(item(firstVariant, home, 2, "8500"),
                        item(secondVariant, office, 99, "9500")), null));
        assertThatThrownBy(() -> sales.confirmDraft(draft.id()))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(sales.get(draft.id()).status()).isEqualTo(SaleStatus.DRAFT);
        assertThat(inventory.getBalance(home, firstVariant).reserved()).isZero();
        assertThat(inventory.getBalance(office, secondVariant).reserved()).isZero();
        assertThat(count("payments")).isZero();
    }

    @Test
    void draftCreateRejectsIdempotencyKeyWithDifferentData() {
        UUID key = UUID.randomUUID();
        sales.createDraft(new CreateDraftSaleRequest(key, customer, null, List.of(), "A"));
        assertThatThrownBy(() -> sales.createDraft(
                new CreateDraftSaleRequest(key, customer, null, List.of(), "B")))
                .isInstanceOf(SaleRuleViolationException.class);
    }

    @Test
    void closingSalesSnapshotFreezesFulfilledPickupAndDiagnosesLateCompletion() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        var draft = sales.createDraft(new CreateDraftSaleRequest(
                UUID.randomUUID(), customer, FulfillmentType.SELF_PICKUP,
                List.of(item(firstVariant, home, 1, "8500")), null));
        var pending = sales.create(request(UUID.randomUUID(), customer,
                item(firstVariant, home, 1, "8500")));
        var done = sales.create(request(UUID.randomUUID(), customer,
                item(firstVariant, home, 1, "8500")));
        assertThat(kg.chairx.finance.application.DailyClosingSalesSnapshot.live(jdbcClient,date))
                .isEmpty();

        sales.fulfill(done.id());
        var before = kg.chairx.finance.application.DailyClosingSalesSnapshot.read(jdbcClient,date);
        assertThat(before.closed()).isFalse();
        assertThat(before.preliminary()).isTrue();
        assertThat(before.totals().orders()).isEqualTo(1);
        assertThat(before.items()).extracting(
                kg.chairx.finance.application.DailyClosingSalesSnapshot.CompletedSale::saleId)
                .containsExactly(done.id());

        UUID closingId = UUID.randomUUID();
        jdbc.update("INSERT INTO finance_daily_closings(id,business_date,created_by) VALUES(?,?,'sale-test')",
                closingId, java.sql.Date.valueOf(date));
        try {
            transactions.executeWithoutResult(tx ->
                    kg.chairx.finance.application.DailyClosingSalesSnapshot.capture(
                            jdbcClient, closingId, date));
            var frozen = kg.chairx.finance.application.DailyClosingSalesSnapshot.read(jdbcClient,date);
            assertThat(frozen.snapshotAvailable()).isTrue();
            assertThat(frozen.closed()).isTrue();
            assertThat(frozen.preliminary()).isFalse();
            assertThat(frozen.totals().orders()).isEqualTo(1);
            assertThat(frozen.totals().chairs()).isEqualTo(1);
            assertThat(frozen.totals().value()).isEqualByComparingTo("8500");
            assertThat(frozen.lateCompletionCount()).isZero();

            sales.fulfill(pending.id());
            var later = kg.chairx.finance.application.DailyClosingSalesSnapshot.read(jdbcClient,date);
            assertThat(later.items()).hasSize(1);
            assertThat(later.totals().value()).isEqualByComparingTo("8500");
            assertThat(later.lateCompletionCount()).isEqualTo(1);
            assertThat(sales.get(draft.id()).status()).isEqualTo(SaleStatus.DRAFT);
        } finally {
            jdbc.update("DELETE FROM finance_daily_closings WHERE id=?", closingId);
        }
    }

    @Test
    void historicClosingWithoutSnapshotIsExplicitlyUnavailable() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO finance_daily_closings(id,business_date,created_by) VALUES(?,?,'legacy')",
                id, java.sql.Date.valueOf(date));
        try {
            var historic = kg.chairx.finance.application.DailyClosingSalesSnapshot.read(jdbcClient,date);
            assertThat(historic.closed()).isTrue();
            assertThat(historic.snapshotAvailable()).isFalse();
            assertThat(historic.totals().value()).isNull();
            assertThat(historic.lateCompletionCount()).isNull();
        } finally {
            jdbc.update("DELETE FROM finance_daily_closings WHERE id=?",id);
        }
    }

    @Test
    void creatingSaleReservesStockWithoutPhysicalMovement() {
        long movementsBefore = stockMovementCount();

        var sale = sales.create(
                request(
                        UUID.randomUUID(),
                        customer,
                        item(firstVariant, home, 3, "8500")
                )
        );

        assertThat(sale.id()).isNotNull();
        assertThat(sale.saleNumber())
                .startsWith("SALE-");
        assertThat(sale.customerId())
                .isEqualTo(customer);
        assertThat(sale.status())
                .isEqualTo(SaleStatus.CONFIRMED);
        assertThat(sale.fulfillmentType())
                .isEqualTo(FulfillmentType.SELF_PICKUP);

        assertThat(sale.items())
                .hasSize(1);

        assertThat(sale.total())
                .isEqualByComparingTo("25500.00");

        var balance = inventory.getBalance(
                home,
                firstVariant
        );

        assertThat(balance.onHand())
                .isEqualTo(20);

        assertThat(balance.reserved())
                .isEqualTo(3);

        assertThat(balance.available())
                .isEqualTo(17);

        assertThat(stockMovementCount())
                .isEqualTo(movementsBefore);

        assertThat(count("sales"))
                .isEqualTo(1);

        assertThat(count("sale_items"))
                .isEqualTo(1);
    }

    @Test
    void anonymousSaleDoesNotRequireCustomer() {
        var sale = sales.create(
                request(
                        UUID.randomUUID(),
                        null,
                        item(firstVariant, home, 1, "9000")
                )
        );

        assertThat(sale.customerId())
                .isNull();

        assertThat(sale.status())
                .isEqualTo(SaleStatus.CONFIRMED);

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).reserved()
        ).isEqualTo(1);
    }

    @Test
    void multipleWarehousesAreReservedInOneSale() {
        var sale = sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customer,
                        FulfillmentType.CITY_DELIVERY,
                        List.of(
                                item(
                                        firstVariant,
                                        home,
                                        2,
                                        "8500"
                                ),
                                item(
                                        secondVariant,
                                        office,
                                        3,
                                        "9500"
                                )
                        )
                )
        );

        assertThat(sale.items())
                .hasSize(2);

        assertThat(sale.total())
                .isEqualByComparingTo("45500.00");

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).reserved()
        ).isEqualTo(2);

        assertThat(
                inventory.getBalance(
                        office,
                        secondVariant
                ).reserved()
        ).isEqualTo(3);
    }

    @Test
    void insufficientStockRollsBackEntireSale() {
        long auditBefore = saleAuditCount();

        assertThatThrownBy(
                () -> sales.create(
                        request(
                                UUID.randomUUID(),
                                customer,
                                item(
                                        firstVariant,
                                        home,
                                        21,
                                        "8500"
                                )
                        )
                )
        ).isInstanceOf(InsufficientStockException.class);

        assertThat(count("sales"))
                .isZero();

        assertThat(count("sale_items"))
                .isZero();

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

        assertThat(saleAuditCount())
                .isEqualTo(auditBefore);
    }

    @Test
    void failureOnSecondItemRollsBackFirstReservationAndSale() {
        long auditBefore = saleAuditCount();

        var request = new CreateSaleRequest(
                UUID.randomUUID(),
                customer,
                FulfillmentType.CITY_DELIVERY,
                List.of(
                        item(
                                firstVariant,
                                home,
                                2,
                                "8500"
                        ),
                        item(
                                secondVariant,
                                office,
                                21,
                                "9500"
                        )
                )
        );

        assertThatThrownBy(
                () -> sales.create(request)
        ).isInstanceOf(InsufficientStockException.class);

        assertThat(count("sales"))
                .isZero();

        assertThat(count("sale_items"))
                .isZero();

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).reserved()
        ).isZero();

        assertThat(
                inventory.getBalance(
                        office,
                        secondVariant
                ).reserved()
        ).isZero();

        assertThat(saleAuditCount())
                .isEqualTo(auditBefore);
    }

    @Test
    void identicalCreateRetryReturnsSameSaleWithoutSecondReservation() {
        UUID key = UUID.randomUUID();

        var request = request(
                key,
                customer,
                item(firstVariant, home, 2, "8500")
        );

        var first = sales.create(request);
        var second = sales.create(request);

        assertThat(second.id())
                .isEqualTo(first.id());

        assertThat(second.saleNumber())
                .isEqualTo(first.saleNumber());

        assertThat(count("sales"))
                .isEqualTo(1);

        assertThat(count("sale_items"))
                .isEqualTo(1);

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).reserved()
        ).isEqualTo(2);

        assertThat(saleAuditCount())
                .isEqualTo(1);
    }

    @Test
    void idempotencyKeyCannotBeReusedWithDifferentSale() {
        UUID key = UUID.randomUUID();

        sales.create(
                request(
                        key,
                        customer,
                        item(firstVariant, home, 1, "8500")
                )
        );

        assertThatThrownBy(
                () -> sales.create(
                        request(
                                key,
                                customer,
                                item(
                                        firstVariant,
                                        home,
                                        2,
                                        "8500"
                                )
                        )
                )
        )
                .isInstanceOfSatisfying(
                        SaleRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(
                                        "SALE_IDEMPOTENCY_CONFLICT"
                                )
                );

        assertThat(count("sales"))
                .isEqualTo(1);

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).reserved()
        ).isEqualTo(1);
    }

    @Test
    void inactiveCustomerCannotBeUsedForNewSale() {
        jdbc.update(
                "update customers set active=false where id=?",
                customer
        );

        assertThatThrownBy(
                () -> sales.create(
                        request(
                                UUID.randomUUID(),
                                customer,
                                item(
                                        firstVariant,
                                        home,
                                        1,
                                        "8500"
                                )
                        )
                )
        )
                .isInstanceOfSatisfying(
                        SaleRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo("CUSTOMER_INACTIVE")
                );

        assertThat(count("sales"))
                .isZero();

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).reserved()
        ).isZero();
    }

    @Test
    void duplicateVariantAndWarehouseLineIsRejected() {
        var request = new CreateSaleRequest(
                UUID.randomUUID(),
                customer,
                FulfillmentType.SELF_PICKUP,
                List.of(
                        item(
                                firstVariant,
                                home,
                                1,
                                "8500"
                        ),
                        item(
                                firstVariant,
                                home,
                                1,
                                "8500"
                        )
                )
        );

        assertThatThrownBy(
                () -> sales.create(request)
        )
                .isInstanceOfSatisfying(
                        SaleRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(
                                        "DUPLICATE_SALE_ITEM"
                                )
                );

        assertThat(count("sales"))
                .isZero();

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).reserved()
        ).isZero();
    }

    @Test
    void cancellingSaleReleasesReservationWithoutPhysicalMovement() {
        var sale = sales.create(
                request(
                        UUID.randomUUID(),
                        customer,
                        item(firstVariant, home, 4, "8500")
                )
        );

        long movementsBefore = stockMovementCount();

        var cancelled = sales.cancel(sale.id());

        assertThat(cancelled.status())
                .isEqualTo(SaleStatus.CANCELLED);

        assertThat(cancelled.cancelledAt())
                .isNotNull();

        assertThat(cancelled.cancelledBy())
                .isNotBlank();

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

        assertThat(stockMovementCount())
                .isEqualTo(movementsBefore);

        assertThat(saleAuditCount())
                .isEqualTo(2);
    }

    @Test
    void repeatedCancelDoesNotReleaseReservationTwice() {
        var sale = sales.create(
                request(
                        UUID.randomUUID(),
                        customer,
                        item(firstVariant, home, 2, "8500")
                )
        );

        var first = sales.cancel(sale.id());
        var second = sales.cancel(sale.id());

        assertThat(second.status())
                .isEqualTo(SaleStatus.CANCELLED);

        assertThat(second.cancelledAt())
                .isEqualTo(first.cancelledAt());

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).reserved()
        ).isZero();

        assertThat(saleAuditCount())
                .isEqualTo(2);
    }

    @Test
    void fulfillingSaleReleasesReservationAndCreatesSaleOut() {
        var sale = sales.create(
                request(
                        UUID.randomUUID(),
                        customer,
                        item(firstVariant, home, 3, "8500")
                )
        );

        long movementsBefore = stockMovementCount();

        var fulfilled = sales.fulfill(sale.id());

        assertThat(fulfilled.status())
                .isEqualTo(SaleStatus.FULFILLED);

        assertThat(fulfilled.fulfilledAt())
                .isNotNull();

        assertThat(fulfilled.fulfilledBy())
                .isNotBlank();

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

        assertThat(stockMovementCount())
                .isEqualTo(movementsBefore + 1);

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from stock_movements
                        where movement_type='SALE_OUT'
                          and source_type='SALE_ITEM'
                          and source_id=?
                        """,
                        Integer.class,
                        sale.items().getFirst().id()
                )
        ).isEqualTo(1);

        assertThat(saleAuditCount())
                .isEqualTo(2);
    }

    @Test
    void fulfillingMultiWarehouseSaleClosesEverythingTogether() {
        var sale = sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customer,
                        FulfillmentType.SELF_PICKUP,
                        List.of(
                                item(
                                        firstVariant,
                                        home,
                                        2,
                                        "8500"
                                ),
                                item(
                                        secondVariant,
                                        office,
                                        3,
                                        "9500"
                                )
                        )
                )
        );

        var fulfilled = sales.fulfill(sale.id());

        assertThat(fulfilled.status())
                .isEqualTo(SaleStatus.FULFILLED);

        var homeBalance = inventory.getBalance(
                home,
                firstVariant
        );

        var officeBalance = inventory.getBalance(
                office,
                secondVariant
        );

        assertThat(homeBalance.onHand())
                .isEqualTo(18);

        assertThat(homeBalance.reserved())
                .isZero();

        assertThat(officeBalance.onHand())
                .isEqualTo(17);

        assertThat(officeBalance.reserved())
                .isZero();

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from stock_movements
                        where movement_type='SALE_OUT'
                          and source_type='SALE_ITEM'
                        """,
                        Integer.class
                )
        ).isEqualTo(2);
    }

    @Test
    void repeatedFulfillDoesNotCreateSecondPhysicalMovement() {
        var sale = sales.create(
                request(
                        UUID.randomUUID(),
                        customer,
                        item(firstVariant, home, 2, "8500")
                )
        );

        var first = sales.fulfill(sale.id());

        long movementsAfterFirst =
                stockMovementCount();

        var second = sales.fulfill(sale.id());

        assertThat(second.status())
                .isEqualTo(SaleStatus.FULFILLED);

        assertThat(second.fulfilledAt())
                .isEqualTo(first.fulfilledAt());

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).onHand()
        ).isEqualTo(18);

        assertThat(stockMovementCount())
                .isEqualTo(movementsAfterFirst);

        assertThat(saleAuditCount())
                .isEqualTo(2);
    }

    @Test
    void fulfilledSaleCannotBeCancelled() {
        var sale = sales.create(
                request(
                        UUID.randomUUID(),
                        customer,
                        item(firstVariant, home, 1, "8500")
                )
        );

        sales.fulfill(sale.id());

        assertThatThrownBy(
                () -> sales.cancel(sale.id())
        )
                .isInstanceOfSatisfying(
                        SaleRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(
                                        "SALE_ALREADY_FULFILLED"
                                )
                );

        assertThat(
                sales.get(sale.id()).status()
        ).isEqualTo(SaleStatus.FULFILLED);

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).onHand()
        ).isEqualTo(19);
    }

    @Test
    void cancelledSaleCannotBeFulfilled() {
        var sale = sales.create(
                request(
                        UUID.randomUUID(),
                        customer,
                        item(firstVariant, home, 1, "8500")
                )
        );

        sales.cancel(sale.id());

        assertThatThrownBy(
                () -> sales.fulfill(sale.id())
        )
                .isInstanceOfSatisfying(
                        SaleRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(
                                        "SALE_ALREADY_CANCELLED"
                                )
                );

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).onHand()
        ).isEqualTo(20);
    }

    @Test
    void fulfillmentFailureRollsBackReservationMovementAndStatus() {
        var sale = sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customer,
                        FulfillmentType.SELF_PICKUP,
                        List.of(
                                item(
                                        firstVariant,
                                        home,
                                        2,
                                        "8500"
                                ),
                                item(
                                        secondVariant,
                                        office,
                                        2,
                                        "9500"
                                )
                        )
                )
        );

        long movementsBefore = stockMovementCount();
        long auditBefore = saleAuditCount();

        jdbc.execute("""
                create function test_reject_second_sale_out()
                returns trigger
                language plpgsql
                as $$
                begin
                    if NEW.movement_type = 'SALE_OUT'
                       and NEW.product_variant_id = '%s'::uuid
                    then
                        raise exception 'Simulated second SALE_OUT failure';
                    end if;

                    return NEW;
                end;
                $$
                """.formatted(secondVariant));

        jdbc.execute("""
                create trigger test_reject_second_sale_out
                before insert on stock_movements
                for each row
                execute function test_reject_second_sale_out()
                """);

        try {
            assertThatThrownBy(
                    () -> sales.fulfill(sale.id())
            ).isInstanceOf(DataAccessException.class);

            var stored = sales.get(sale.id());

            assertThat(stored.status())
                    .isEqualTo(SaleStatus.CONFIRMED);

            var homeBalance = inventory.getBalance(
                    home,
                    firstVariant
            );

            var officeBalance = inventory.getBalance(
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

            assertThat(saleAuditCount())
                    .isEqualTo(auditBefore);
        } finally {
            jdbc.execute("""
                    drop trigger test_reject_second_sale_out
                    on stock_movements
                    """);

            jdbc.execute("""
                    drop function test_reject_second_sale_out()
                    """);
        }
    }
    @Test
    void concurrentIdenticalCreateReturnsSameSaleWithoutDoubleReservation()
            throws Exception {

        UUID idempotencyKey = UUID.randomUUID();

        var request = request(
                idempotencyKey,
                customer,
                item(
                        firstVariant,
                        home,
                        2,
                        "8500"
                )
        );

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {

            var task = (java.util.concurrent.Callable<kg.chairx.sale.api.SaleResponse>) () -> {
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                "concurrent-sale-test-user",
                                null,
                                List.of()
                        )
                );

                try {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Concurrent sale test did not start in time"
                        );
                    }

                    return sales.create(request);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };

            Future<kg.chairx.sale.api.SaleResponse> first =
                    executor.submit(task);

            Future<kg.chairx.sale.api.SaleResponse> second =
                    executor.submit(task);

            assertThat(
                    ready.await(5, TimeUnit.SECONDS)
            ).isTrue();

            start.countDown();

            var firstResult =
                    first.get(10, TimeUnit.SECONDS);

            var secondResult =
                    second.get(10, TimeUnit.SECONDS);

            assertThat(secondResult.id())
                    .isEqualTo(firstResult.id());

            assertThat(secondResult.saleNumber())
                    .isEqualTo(firstResult.saleNumber());

            assertThat(firstResult.status())
                    .isEqualTo(SaleStatus.CONFIRMED);

            assertThat(secondResult.status())
                    .isEqualTo(SaleStatus.CONFIRMED);

            assertThat(count("sales"))
                    .isEqualTo(1);

            assertThat(count("sale_items"))
                    .isEqualTo(1);

            var balance = inventory.getBalance(
                    home,
                    firstVariant
            );

            assertThat(balance.onHand())
                    .isEqualTo(20);

            assertThat(balance.reserved())
                    .isEqualTo(2);

            assertThat(balance.available())
                    .isEqualTo(18);

            assertThat(saleAuditCount())
                    .isEqualTo(1);
        }
    }


    @Test
    void concurrentDifferentSalesCannotReserveTheLastUnitTwice() throws Exception {
        // The fixture starts with 20 units. Reserve 19 before the race.
        sales.create(request(UUID.randomUUID(), customer,
                item(firstVariant, home, 19, "8500")));

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Object> task = () -> {
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                "last-unit-test-user", null, List.of()));
                try {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent sale start timed out");
                    }
                    try {
                        return sales.create(request(UUID.randomUUID(), customer,
                                item(firstVariant, home, 1, "8500")));
                    } catch (InsufficientStockException expected) {
                        return expected;
                    }
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };

            Future<Object> first = executor.submit(task);
            Future<Object> second = executor.submit(task);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Object a = first.get(15, TimeUnit.SECONDS);
            Object b = second.get(15, TimeUnit.SECONDS);
            assertThat(List.of(a, b).stream()
                    .filter(kg.chairx.sale.api.SaleResponse.class::isInstance).count())
                    .isEqualTo(1);
            assertThat(List.of(a, b).stream()
                    .filter(InsufficientStockException.class::isInstance).count())
                    .isEqualTo(1);
        }

        assertThat(count("sales")).isEqualTo(2);
        assertThat(count("sale_items")).isEqualTo(2);
        var balance = inventory.getBalance(home, firstVariant);
        assertThat(balance.onHand()).isEqualTo(20);
        assertThat(balance.reserved()).isEqualTo(20);
        assertThat(balance.available()).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from stock_movements where movement_type='SALE_OUT'",
                Long.class)).isZero();
        assertThat(saleAuditCount()).isEqualTo(2);
    }

    private CreateSaleRequest request(
            UUID idempotencyKey,
            UUID customerId,
            CreateSaleItemRequest item
    ) {
        return new CreateSaleRequest(
                idempotencyKey,
                customerId,
                FulfillmentType.SELF_PICKUP,
                List.of(item)
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
                "sale-test"
        );
    }

    private void clearSaleData() {
        assertTestDatabase();

        jdbc.execute("""
                truncate table
                    inventory_transfer_cost_origins,
                    inventory_transfers,
                    inventory_cost_allocations,
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

        /*
         * sale_number_seq не принадлежит колонке таблицы,
         * поэтому TRUNCATE ... RESTART IDENTITY его не сбрасывает.
         */
        jdbc.execute(
                "alter sequence sale_number_seq restart with 1"
        );

        jdbc.update("""
                delete from audit_entries
                where entity_type in (
                    'SALE',
                    'STOCK_MOVEMENT'
                )
                """);
    }

    private void assertTestDatabase() {
        assertThat(
                jdbc.queryForObject(
                        "select current_database()",
                        String.class
                )
        ).isEqualTo("chairx_test");
    }

    private long stockMovementCount() {
        return jdbc.queryForObject(
                "select count(*) from stock_movements",
                Long.class
        );
    }

    private long saleAuditCount() {
        return jdbc.queryForObject(
                """
                select count(*)
                from audit_entries
                where entity_type='SALE'
                """,
                Long.class
        );
    }

    private int count(String table) {
        return jdbc.queryForObject(
                "select count(*) from " + table,
                Integer.class
        );
    }
}