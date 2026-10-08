package kg.chairx.returning;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.returning.api.CreateReturnItemRequest;
import kg.chairx.returning.api.CreateReturnRequest;
import kg.chairx.returning.application.ReturnService;
import kg.chairx.returning.domain.ReturnCondition;
import kg.chairx.returning.application.ReturnRuleViolationException;
import kg.chairx.sale.api.CreateSaleItemRequest;
import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.domain.FulfillmentType;
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
class ReturnTests {

    @Autowired
    ReturnService returns;

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
                        "return-test-user",
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
                values (?, 'Return fixture', true, now(), now())
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
                values (?, 'Return Customer', '+996555000000', true, now(), now())
                """, customer);

        addStock(home, firstVariant, 20);
        addStock(home, secondVariant, 20);
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
    void createsSellableReturnAndRestoresStock() {
        var sale = createFulfilledSale(
                item(firstVariant, home, 3, "8500")
        );

        UUID saleItemId = sale.items().getFirst().id();

        var created = returns.create(
                request(
                        sale.id(),
                        home,
                        UUID.randomUUID(),
                        List.of(
                                returnItem(
                                        saleItemId,
                                        2,
                                        ReturnCondition.SELLABLE
                                )
                        )
                )
        );

        assertThat(created.saleId())
                .isEqualTo(sale.id());

        assertThat(created.warehouseId())
                .isEqualTo(home);

        assertThat(created.items())
                .hasSize(1);

        assertThat(created.items().getFirst().quantity())
                .isEqualTo(2);

        assertThat(created.items().getFirst().condition())
                .isEqualTo(ReturnCondition.SELLABLE);

        assertThat(created.reason())
                .isEqualTo("Возврат клиента");

        assertThat(created.createdBy())
                .isEqualTo("return-test-user");

        assertThat(created.createdAt())
                .isNotNull();

        var balance = inventory.getBalance(
                home,
                firstVariant
        );

        assertThat(balance.onHand())
                .isEqualTo(19);

        assertThat(balance.reserved())
                .isZero();

        assertThat(balance.blocked())
                .isZero();

        assertThat(balance.available())
                .isEqualTo(19);

        assertThat(returnInCount())
                .isEqualTo(1);

        assertThat(count("returns"))
                .isEqualTo(1);

        assertThat(count("return_items"))
                .isEqualTo(1);
    }

    @Test
    void blockedReturnRestoresOnHandButDoesNotBecomeAvailable() {
        var sale = createFulfilledSale(
                item(firstVariant, home, 2, "8500")
        );

        UUID saleItemId = sale.items().getFirst().id();

        returns.create(
                request(
                        sale.id(),
                        home,
                        UUID.randomUUID(),
                        List.of(
                                returnItem(
                                        saleItemId,
                                        1,
                                        ReturnCondition.BLOCKED
                                )
                        )
                )
        );

        var balance = inventory.getBalance(
                home,
                firstVariant
        );

        assertThat(balance.onHand())
                .isEqualTo(19);

        assertThat(balance.reserved())
                .isZero();

        assertThat(balance.blocked())
                .isEqualTo(1);

        assertThat(balance.available())
                .isEqualTo(18);

        assertThat(returnInCount())
                .isEqualTo(1);

        /*
         * Blocking is not a physical movement.
         * There must be exactly one RETURN_IN and no extra movement.
         */
        assertThat(
                returnPhysicalMovementCount()
        ).isEqualTo(1);
    }

    @Test
    void oneReturnCanContainSellableAndBlockedItems() {
        var sale = createFulfilledSale(
                item(firstVariant, home, 2, "8500"),
                item(secondVariant, home, 3, "9500")
        );

        UUID firstSaleItem = sale.items().stream()
                .filter(item -> item.productVariantId().equals(firstVariant))
                .findFirst()
                .orElseThrow()
                .id();

        UUID secondSaleItem = sale.items().stream()
                .filter(item -> item.productVariantId().equals(secondVariant))
                .findFirst()
                .orElseThrow()
                .id();

        var created = returns.create(
                request(
                        sale.id(),
                        home,
                        UUID.randomUUID(),
                        List.of(
                                returnItem(
                                        firstSaleItem,
                                        1,
                                        ReturnCondition.SELLABLE
                                ),
                                returnItem(
                                        secondSaleItem,
                                        2,
                                        ReturnCondition.BLOCKED
                                )
                        )
                )
        );

        assertThat(created.items())
                .hasSize(2);

        var firstBalance = inventory.getBalance(
                home,
                firstVariant
        );

        var secondBalance = inventory.getBalance(
                home,
                secondVariant
        );

        assertThat(firstBalance.onHand())
                .isEqualTo(19);

        assertThat(firstBalance.blocked())
                .isZero();

        assertThat(firstBalance.available())
                .isEqualTo(19);

        assertThat(secondBalance.onHand())
                .isEqualTo(19);

        assertThat(secondBalance.blocked())
                .isEqualTo(2);

        assertThat(secondBalance.available())
                .isEqualTo(17);

        assertThat(returnInCount())
                .isEqualTo(2);
    }

    @Test
    void allowsMultiplePartialReturnsUpToSoldQuantity() {
        var sale = createFulfilledSale(
                item(firstVariant, home, 5, "8500")
        );

        UUID saleItemId = sale.items().getFirst().id();

        returns.create(
                request(
                        sale.id(),
                        home,
                        UUID.randomUUID(),
                        List.of(
                                returnItem(
                                        saleItemId,
                                        2,
                                        ReturnCondition.SELLABLE
                                )
                        )
                )
        );

        returns.create(
                request(
                        sale.id(),
                        home,
                        UUID.randomUUID(),
                        List.of(
                                returnItem(
                                        saleItemId,
                                        3,
                                        ReturnCondition.SELLABLE
                                )
                        )
                )
        );

        assertThat(count("returns"))
                .isEqualTo(2);

        assertThat(count("return_items"))
                .isEqualTo(2);

        assertThat(returnedQuantity(saleItemId))
                .isEqualTo(5);

        var balance = inventory.getBalance(
                home,
                firstVariant
        );

        assertThat(balance.onHand())
                .isEqualTo(20);

        assertThat(balance.available())
                .isEqualTo(20);

        assertThat(returnInCount())
                .isEqualTo(2);
    }

    @Test
    void rejectsReturnAboveRemainingSoldQuantity() {
        var sale = createFulfilledSale(
                item(firstVariant, home, 3, "8500")
        );

        UUID saleItemId = sale.items().getFirst().id();

        returns.create(
                request(
                        sale.id(),
                        home,
                        UUID.randomUUID(),
                        List.of(
                                returnItem(
                                        saleItemId,
                                        2,
                                        ReturnCondition.SELLABLE
                                )
                        )
                )
        );

        long movementsBefore = stockMovementCount();

        assertThatThrownBy(
                () -> returns.create(
                        request(
                                sale.id(),
                                home,
                                UUID.randomUUID(),
                                List.of(
                                        returnItem(
                                                saleItemId,
                                                2,
                                                ReturnCondition.SELLABLE
                                        )
                                )
                        )
                )
        ).isInstanceOf(ReturnRuleViolationException.class);

        assertThat(count("returns"))
                .isEqualTo(1);

        assertThat(returnedQuantity(saleItemId))
                .isEqualTo(2);

        assertThat(stockMovementCount())
                .isEqualTo(movementsBefore);

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).onHand()
        ).isEqualTo(19);
    }

    @Test
    void rejectsReturnForConfirmedSaleThatHasNotLeftWarehouse() {
        var sale = createSale(
                item(firstVariant, home, 2, "8500")
        );

        UUID saleItemId = sale.items().getFirst().id();

        assertThatThrownBy(
                () -> returns.create(
                        request(
                                sale.id(),
                                home,
                                UUID.randomUUID(),
                                List.of(
                                        returnItem(
                                                saleItemId,
                                                1,
                                                ReturnCondition.SELLABLE
                                        )
                                )
                        )
                )
        ).isInstanceOf(ReturnRuleViolationException.class);

        assertThat(count("returns"))
                .isZero();

        assertThat(returnInCount())
                .isZero();

        var balance = inventory.getBalance(
                home,
                firstVariant
        );

        assertThat(balance.onHand())
                .isEqualTo(20);

        assertThat(balance.reserved())
                .isEqualTo(2);
    }

    @Test
    void rejectsSaleItemFromAnotherSale() {
        var firstSale = createFulfilledSale(
                item(firstVariant, home, 1, "8500")
        );

        var secondSale = createFulfilledSale(
                item(secondVariant, home, 1, "9500")
        );

        UUID foreignSaleItem =
                secondSale.items().getFirst().id();

        long movementsBefore = stockMovementCount();

        assertThatThrownBy(
                () -> returns.create(
                        request(
                                firstSale.id(),
                                home,
                                UUID.randomUUID(),
                                List.of(
                                        returnItem(
                                                foreignSaleItem,
                                                1,
                                                ReturnCondition.SELLABLE
                                        )
                                )
                        )
                )
        ).isInstanceOf(ReturnRuleViolationException.class);

        assertThat(count("returns"))
                .isZero();

        assertThat(stockMovementCount())
                .isEqualTo(movementsBefore);
    }

    @Test
    void rejectsDuplicateSaleItemInsideSameReturn() {
        var sale = createFulfilledSale(
                item(firstVariant, home, 3, "8500")
        );

        UUID saleItemId = sale.items().getFirst().id();

        assertThatThrownBy(
                () -> returns.create(
                        request(
                                sale.id(),
                                home,
                                UUID.randomUUID(),
                                List.of(
                                        returnItem(
                                                saleItemId,
                                                1,
                                                ReturnCondition.SELLABLE
                                        ),
                                        returnItem(
                                                saleItemId,
                                                1,
                                                ReturnCondition.BLOCKED
                                        )
                                )
                        )
                )
        ).isInstanceOf(ReturnRuleViolationException.class);

        assertThat(count("returns"))
                .isZero();

        assertThat(returnInCount())
                .isZero();
    }

    @Test
    void rejectsReturnToInactiveWarehouse() {
        var sale = createFulfilledSale(
                item(firstVariant, home, 1, "8500")
        );

        UUID saleItemId = sale.items().getFirst().id();

        jdbc.update("""
                update warehouses
                set active=false
                where id=?
                """, office);

        try {
            assertThatThrownBy(
                    () -> returns.create(
                            request(
                                    sale.id(),
                                    office,
                                    UUID.randomUUID(),
                                    List.of(
                                            returnItem(
                                                    saleItemId,
                                                    1,
                                                    ReturnCondition.SELLABLE
                                            )
                                    )
                            )
                    )
            ).isInstanceOf(ReturnRuleViolationException.class);

            assertThat(count("returns"))
                    .isZero();

            assertThat(returnInCount())
                    .isZero();
        } finally {
            jdbc.update("""
                    update warehouses
                    set active=true
                    where id=?
                    """, office);
        }
    }

    @Test
    void repeatedSameIdempotencyRequestDoesNotReturnStockTwice() {
        var sale = createFulfilledSale(
                item(firstVariant, home, 2, "8500")
        );

        UUID saleItemId = sale.items().getFirst().id();
        UUID idempotencyKey = UUID.randomUUID();

        var request = request(
                sale.id(),
                home,
                idempotencyKey,
                List.of(
                        returnItem(
                                saleItemId,
                                1,
                                ReturnCondition.SELLABLE
                        )
                )
        );

        var first = returns.create(request);

        long movementsAfterFirst =
                stockMovementCount();

        var repeated = returns.create(request);

        assertThat(repeated.id())
                .isEqualTo(first.id());

        assertThat(count("returns"))
                .isEqualTo(1);

        assertThat(count("return_items"))
                .isEqualTo(1);

        assertThat(stockMovementCount())
                .isEqualTo(movementsAfterFirst);

        assertThat(returnInCount())
                .isEqualTo(1);

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).onHand()
        ).isEqualTo(19);
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsRejected() {
        var sale = createFulfilledSale(
                item(firstVariant, home, 3, "8500")
        );

        UUID saleItemId = sale.items().getFirst().id();
        UUID idempotencyKey = UUID.randomUUID();

        returns.create(
                request(
                        sale.id(),
                        home,
                        idempotencyKey,
                        List.of(
                                returnItem(
                                        saleItemId,
                                        1,
                                        ReturnCondition.SELLABLE
                                )
                        )
                )
        );

        long movementsAfterFirst =
                stockMovementCount();

        assertThatThrownBy(
                () -> returns.create(
                        request(
                                sale.id(),
                                home,
                                idempotencyKey,
                                List.of(
                                        returnItem(
                                                saleItemId,
                                                2,
                                                ReturnCondition.SELLABLE
                                        )
                                )
                        )
                )
        ).isInstanceOf(ReturnRuleViolationException.class);

        assertThat(count("returns"))
                .isEqualTo(1);

        assertThat(returnedQuantity(saleItemId))
                .isEqualTo(1);

        assertThat(stockMovementCount())
                .isEqualTo(movementsAfterFirst);
    }

    @Test
    void idempotencyIgnoresItemOrder() {
        var sale = createFulfilledSale(
                item(firstVariant, home, 2, "8500"),
                item(secondVariant, home, 2, "9500")
        );

        UUID firstSaleItem = sale.items().stream()
                .filter(item -> item.productVariantId().equals(firstVariant))
                .findFirst()
                .orElseThrow()
                .id();

        UUID secondSaleItem = sale.items().stream()
                .filter(item -> item.productVariantId().equals(secondVariant))
                .findFirst()
                .orElseThrow()
                .id();

        UUID key = UUID.randomUUID();

        var first = returns.create(
                request(
                        sale.id(),
                        home,
                        key,
                        List.of(
                                returnItem(
                                        firstSaleItem,
                                        1,
                                        ReturnCondition.SELLABLE
                                ),
                                returnItem(
                                        secondSaleItem,
                                        1,
                                        ReturnCondition.BLOCKED
                                )
                        )
                )
        );

        long movementsAfterFirst =
                stockMovementCount();

        var repeated = returns.create(
                request(
                        sale.id(),
                        home,
                        key,
                        List.of(
                                returnItem(
                                        secondSaleItem,
                                        1,
                                        ReturnCondition.BLOCKED
                                ),
                                returnItem(
                                        firstSaleItem,
                                        1,
                                        ReturnCondition.SELLABLE
                                )
                        )
                )
        );

        assertThat(repeated.id())
                .isEqualTo(first.id());

        assertThat(count("returns"))
                .isEqualTo(1);

        assertThat(count("return_items"))
                .isEqualTo(2);

        assertThat(stockMovementCount())
                .isEqualTo(movementsAfterFirst);
    }

    @Test
    void returnCanBeReceivedIntoDifferentWarehouse() {
        var sale = createFulfilledSale(
                item(firstVariant, home, 2, "8500")
        );

        UUID saleItemId = sale.items().getFirst().id();

        returns.create(
                request(
                        sale.id(),
                        office,
                        UUID.randomUUID(),
                        List.of(
                                returnItem(
                                        saleItemId,
                                        1,
                                        ReturnCondition.SELLABLE
                                )
                        )
                )
        );

        var homeBalance = inventory.getBalance(
                home,
                firstVariant
        );

        var officeBalance = inventory.getBalance(
                office,
                firstVariant
        );

        assertThat(homeBalance.onHand())
                .isEqualTo(18);

        assertThat(officeBalance.onHand())
                .isEqualTo(1);

        assertThat(officeBalance.available())
                .isEqualTo(1);
    }

    @Test
    void stockFailureRollsBackWholeReturn() {
        var sale = createFulfilledSale(
                item(firstVariant, home, 1, "8500"),
                item(secondVariant, home, 1, "9500")
        );

        UUID firstSaleItem = sale.items().stream()
                .filter(item -> item.productVariantId().equals(firstVariant))
                .findFirst()
                .orElseThrow()
                .id();

        UUID secondSaleItem = sale.items().stream()
                .filter(item -> item.productVariantId().equals(secondVariant))
                .findFirst()
                .orElseThrow()
                .id();

        long movementsBefore =
                stockMovementCount();

        long firstOnHandBefore =
                inventory.getBalance(
                        home,
                        firstVariant
                ).onHand();

        long secondOnHandBefore =
                inventory.getBalance(
                        home,
                        secondVariant
                ).onHand();

        jdbc.execute("""
                create function test_reject_return_second_movement()
                returns trigger
                language plpgsql
                as $$
                begin
                    if NEW.movement_type = 'RETURN_IN'
                       and NEW.source_type = 'SALE_RETURN'
                       and NEW.product_variant_id = '%s'::uuid
                    then
                        raise exception 'Simulated return failure';
                    end if;

                    return NEW;
                end;
                $$
                """.formatted(secondVariant));

        jdbc.execute("""
                create trigger test_reject_return_second_movement
                before insert on stock_movements
                for each row
                execute function test_reject_return_second_movement()
                """);

        try {
            assertThatThrownBy(
                    () -> returns.create(
                            request(
                                    sale.id(),
                                    home,
                                    UUID.randomUUID(),
                                    List.of(
                                            returnItem(
                                                    firstSaleItem,
                                                    1,
                                                    ReturnCondition.SELLABLE
                                            ),
                                            returnItem(
                                                    secondSaleItem,
                                                    1,
                                                    ReturnCondition.SELLABLE
                                            )
                                    )
                            )
                    )
            ).isInstanceOf(DataAccessException.class);

            assertThat(count("returns"))
                    .isZero();

            assertThat(count("return_items"))
                    .isZero();

            assertThat(stockMovementCount())
                    .isEqualTo(movementsBefore);

            assertThat(
                    inventory.getBalance(
                            home,
                            firstVariant
                    ).onHand()
            ).isEqualTo(firstOnHandBefore);

            assertThat(
                    inventory.getBalance(
                            home,
                            secondVariant
                    ).onHand()
            ).isEqualTo(secondOnHandBefore);

            assertThat(returnInCount())
                    .isZero();
        } finally {
            jdbc.execute("""
                    drop trigger test_reject_return_second_movement
                    on stock_movements
                    """);

            jdbc.execute("""
                    drop function test_reject_return_second_movement()
                    """);
        }
    }

    @Test
    void storesReturnAuditEntry() {
        var sale = createFulfilledSale(
                item(firstVariant, home, 1, "8500")
        );

        UUID saleItemId = sale.items().getFirst().id();

        var created = returns.create(
                request(
                        sale.id(),
                        home,
                        UUID.randomUUID(),
                        List.of(
                                returnItem(
                                        saleItemId,
                                        1,
                                        ReturnCondition.SELLABLE
                                )
                        )
                )
        );

        Long auditCount = jdbc.queryForObject("""
                select count(*)
                from audit_entries
                where entity_type='RETURN'
                  and entity_id=?
                  and action='CREATED'
                  and actor='return-test-user'
                """, Long.class, created.id());

        assertThat(auditCount)
                .isEqualTo(1);
    }

    private kg.chairx.sale.api.SaleResponse createSale(
            CreateSaleItemRequest... items
    ) {
        return sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customer,
                        FulfillmentType.SELF_PICKUP,
                        List.of(items)
                )
        );
    }

    private kg.chairx.sale.api.SaleResponse createFulfilledSale(
            CreateSaleItemRequest... items
    ) {
        var sale = createSale(items);

        return sales.fulfill(
                sale.id()
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

    private CreateReturnItemRequest returnItem(
            UUID saleItemId,
            long quantity,
            ReturnCondition condition
    ) {
        return new CreateReturnItemRequest(
                saleItemId,
                quantity,
                condition
        );
    }

    private CreateReturnRequest request(
            UUID saleId,
            UUID warehouseId,
            UUID idempotencyKey,
            List<CreateReturnItemRequest> items
    ) {
        return new CreateReturnRequest(
                saleId,
                warehouseId,
                idempotencyKey,
                items,
                "Возврат клиента",
                "Integration test"
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
                        "RETURN_TEST_FIXTURE",
                        UUID.randomUUID(),
                        "return-test"
                )
        );
    }

    private long returnedQuantity(UUID saleItemId) {
        Long result = jdbc.queryForObject("""
                select coalesce(sum(quantity), 0)
                from return_items
                where sale_item_id=?
                """, Long.class, saleItemId);

        return result == null ? 0 : result;
    }

    private long count(String table) {
        return jdbc.queryForObject(
                "select count(*) from " + table,
                Long.class
        );
    }

    private long stockMovementCount() {
        return count("stock_movements");
    }

    private long returnInCount() {
        return jdbc.queryForObject("""
                select count(*)
                from stock_movements
                where movement_type='RETURN_IN'
                  and source_type='SALE_RETURN'
                """, Long.class);
    }

    private long returnPhysicalMovementCount() {
        return jdbc.queryForObject("""
                select count(*)
                from stock_movements
                where source_type='SALE_RETURN'
                """, Long.class);
    }

    private void clearData() {
        assertTestDatabase();

        jdbc.execute("""
                truncate table
                    inventory_cost_movements, inventory_cost_allocations, inventory_cost_layers, exchange_settlements, exchanges, refunds,
                    return_items,
                    returns,
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
                    'RETURN',
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