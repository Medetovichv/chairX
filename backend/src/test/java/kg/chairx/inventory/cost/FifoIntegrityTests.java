package kg.chairx.inventory.cost;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.application.InventoryOperationConflictException;
import kg.chairx.inventory.domain.StockMovementType;
import kg.chairx.sale.api.*;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.returning.api.*;
import kg.chairx.returning.application.ReturnService;
import kg.chairx.returning.application.ReturnRuleViolationException;
import kg.chairx.returning.domain.ReturnCondition;
import kg.chairx.delivery.api.*;
import kg.chairx.delivery.application.DeliveryService;
import kg.chairx.defect.application.DefectService;
import kg.chairx.defect.application.DefectRuleViolationException;
import kg.chairx.exchange.api.CreateExchangeRequest;
import kg.chairx.exchange.application.ExchangeService;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.purchase.api.*;
import kg.chairx.purchase.application.PurchaseService;
import kg.chairx.operations.ReceivePurchase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import static org.assertj.core.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(kg.chairx.FundedFinanceExtension.class)
@SpringBootTest(properties={"CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@WithMockUser(username="fifo-test")
@Timeout(30)
class FifoIntegrityTests {
    @Autowired InventoryAdjustmentService adjustments;
    @Autowired InventoryCostPostingService posting;
    @Autowired InventoryService inventory;
    @Autowired SaleService sales;
    @Autowired ReturnService returns;
    @Autowired DeliveryService deliveries;
    @Autowired DefectService defects;
    @Autowired ExchangeService exchanges;
    @Autowired PaymentService payments;
    @Autowired PurchaseService purchases;
    @Autowired ReceivePurchase receiving;
    @Autowired JdbcTemplate jdbc;
    UUID home,office,product,variant,other,customer,supplier;

    @BeforeEach void setup() {
        clear();
        home=jdbc.queryForObject("select id from warehouses where code='HOME'",UUID.class);
        office=jdbc.queryForObject("select id from warehouses where code='OFFICE'",UUID.class);
        product=UUID.randomUUID(); variant=UUID.randomUUID(); other=UUID.randomUUID(); customer=UUID.randomUUID(); supplier=UUID.randomUUID();
        jdbc.update("insert into products(id,name,active,created_at,updated_at) values (?,'FIFO test',true,now(),now())",product);
        for(var id:List.of(variant,other)) jdbc.update("insert into product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at) values (?,?,'FIFO',10000,true,now(),now())",id,product);
        jdbc.update("insert into customers(id,full_name,phone,active,created_at,updated_at) values (?,'FIFO','+996555444444',true,now(),now())",customer);
        jdbc.update("insert into suppliers(id,name,active,created_at,updated_at) values (?,'FIFO',true,now(),now())",supplier);
    }
    @AfterEach void cleanup() {
        clear();
        jdbc.update("delete from customers where id=?",customer);
        jdbc.update("delete from product_variants where product_id=?",product);
        jdbc.update("delete from products where id=?",product);
        jdbc.update("delete from suppliers where id=?",supplier);
    }
    void clear() {
        assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("chairx_test");
        jdbc.execute("""
                truncate inventory_transfer_cost_origins, inventory_transfers, inventory_cost_restorations,inventory_cost_write_offs,inventory_cost_allocations,
                inventory_cost_movements,inventory_cost_layers,exchange_settlements,exchanges,refunds,
                return_items,returns,payments,deliveries,sale_items,sales,defects,purchase_receipt_items,
                purchase_receipts,purchase_items,purchases,stock_movements,inventory_balances
                """);
        jdbc.update("delete from audit_entries");
    }
    void stock(UUID warehouse,UUID item,long qty,String cost) {
        adjustments.recordValuedAdjustmentIn(UUID.randomUUID(),warehouse,item,qty,new BigDecimal(cost),"fifo-test");
    }
    SaleResponse sale(long qty) { return sale(home,variant,qty,FulfillmentType.SELF_PICKUP); }
    SaleResponse sale(UUID warehouse,UUID item,long qty,FulfillmentType type) {
        return sales.create(new CreateSaleRequest(UUID.randomUUID(),customer,type,List.of(new CreateSaleItemRequest(item,warehouse,qty,new BigDecimal("10000")))));
    }
    CreateReturnRequest request(SaleResponse sale,UUID warehouse,long qty,ReturnCondition condition) {
        return new CreateReturnRequest(sale.id(),warehouse,UUID.randomUUID(),List.of(new CreateReturnItemRequest(sale.items().getFirst().id(),qty,condition)),"Возврат",null);
    }
    long count(String table) { return jdbc.queryForObject("select count(*) from "+table,Long.class); }
    BigDecimal restored() { return jdbc.queryForObject("select coalesce(sum(amount),0) from inventory_cost_restorations",BigDecimal.class); }
    void consistent() {
        assertThat(count("inventory_cost_balance_discrepancies")).isZero();
        assertThat(count("inventory_unvalued_movements")).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from inventory_cost_layers l where l.remaining_cost <>
                    (select coalesce(sum(case when direction='IN' then amount else -amount end),0)
                     from inventory_cost_movements m where m.cost_layer_id=l.id)
                """,Long.class)).isZero();
    }
    @Test void partialMultiLayerReturnsRestoreHistoricalCostsAndFinalRemainder() {
        stock(home,variant,2,"10000"); stock(home,variant,3,"18000");
        var sale=sale(4); sales.fulfill(sale.id());
        var first=returns.create(request(sale,office,3,ReturnCondition.SELLABLE));
        assertThat(restored()).isEqualByComparingTo("16000");
        assertThat(inventory.getBalance(office,variant).onHand()).isEqualTo(3);
        assertThat(count("inventory_cost_restorations")).isEqualTo(2);
        var second=returns.create(request(sale,home,1,ReturnCondition.SELLABLE));
        assertThat(restored()).isEqualByComparingTo("22000");
        assertThat(count("inventory_cost_allocations")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select min(l.received_at) >= (select max(occurred_at) from stock_movements where movement_type='SALE_OUT') from inventory_cost_layers l join stock_movements m on m.id=l.source_movement_id where m.movement_type='RETURN_IN'",Boolean.class)).isTrue();
        consistent();
    }
    @Test
    void saleUpdatesPhysicalStockAndFifoCostTogether() {
        // 1. Поступило 10 кресел общей стоимостью 50 000 сом.
        stock(home, variant, 10, "50000");

        assertThat(inventory.getBalance(home, variant).onHand())
                .isEqualTo(10);

        // 2. Создаём продажу трёх кресел.
        var created = sale(3);

        // 3. Подтверждаем фактическую выдачу товара.
        sales.fulfill(created.id());

        // 4. На складе должно остаться семь кресел.
        assertThat(inventory.getBalance(home, variant).onHand())
                .isEqualTo(7);

        // 5. FIFO должен списать себестоимость 15 000 сом.
        BigDecimal consumedCost = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(allocated_cost), 0)
                FROM inventory_cost_allocations
                """,
                BigDecimal.class
        );

        assertThat(consumedCost)
                .isEqualByComparingTo("15000");

        // 6. Остаточная стоимость товара — 35 000 сом.
        BigDecimal remainingCost = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(remaining_cost), 0)
                FROM inventory_cost_layers
                """,
                BigDecimal.class
        );

        assertThat(remainingCost)
                .isEqualByComparingTo("35000");

        // 7. Количество и себестоимость должны быть согласованы.
        consistent();
    }

    @Test
    void cannotCreateSaleWhenStockIsInsufficient() {
        // На домашнем складе только одно кресло.
        stock(home, variant, 1, "5000");

        long salesBefore = count("sales");
        long movementsBefore = count("stock_movements");
        long auditBefore = count("audit_entries");

        // Клиент пытается купить два кресла.
        assertThatThrownBy(() -> sale(2))
                .isInstanceOf(RuntimeException.class);

        // Новая продажа не должна сохраниться.
        assertThat(count("sales"))
                .isEqualTo(salesBefore);

        // Физический остаток не изменился.
        var balance = inventory.getBalance(home, variant);

        assertThat(balance.onHand()).isEqualTo(1);
        assertThat(balance.reserved()).isZero();
        assertThat(balance.available()).isEqualTo(1);

        // Никаких новых движений товара.
        assertThat(count("stock_movements"))
                .isEqualTo(movementsBefore);

        // Себестоимость сохранилась.
        BigDecimal remainingCost = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(remaining_cost), 0)
                FROM inventory_cost_layers
                """,
                BigDecimal.class
        );

        assertThat(remainingCost)
                .isEqualByComparingTo("5000");

        // Не должно быть лишних записей аудита.
        assertThat(count("audit_entries"))
                .isEqualTo(auditBefore);

        consistent();
    }

    @Test void fullReturnRestoresAllCostAndLastUnitCanBeSoldAgain() {
        stock(home,variant,1,"5000.01"); var sale=sale(1);sales.fulfill(sale.id());
        var request=request(sale,home,1,ReturnCondition.SELLABLE);
        var result=returns.create(request);
        assertThat(returns.create(request)).isEqualTo(result);
        assertThat(restored()).isEqualByComparingTo("5000.01");
        assertThat(count("stock_movements")).isEqualTo(3);
        var resale=sale(1);sales.fulfill(resale.id());
        assertThat(jdbc.queryForObject("select sum(allocated_cost) from inventory_cost_allocations",BigDecimal.class)).isEqualByComparingTo("10000.02");
        consistent();
    }
    @Test void blockedReturnRetainsValueButIsUnavailable() {
        stock(home,variant,1,"50");var sale=sale(1);sales.fulfill(sale.id());
        returns.create(request(sale,home,1,ReturnCondition.BLOCKED));
        assertThat(restored()).isEqualByComparingTo("50");
        assertThat(inventory.getBalance(home,variant).blocked()).isEqualTo(1);
        assertThat(inventory.getBalance(home,variant).available()).isZero();consistent();
    }
    @Test void partialReturnsConserveFractionalCosts() {
        stock(home,variant,3,"0.01");var sale=sale(3);sales.fulfill(sale.id());
        returns.create(request(sale,home,1,ReturnCondition.SELLABLE));
        assertThat(restored()).isEqualByComparingTo("0.00");
        returns.create(request(sale,home,1,ReturnCondition.SELLABLE));
        assertThat(restored()).isEqualByComparingTo("0.01");
        returns.create(request(sale,home,1,ReturnCondition.SELLABLE));
        assertThat(restored()).isEqualByComparingTo("0.01");consistent();
    }
    DeliveryResponse failedDelivery(SaleResponse sale) {
        var delivery=deliveries.create(new CreateDeliveryRequest(sale.id(),"Клиент","+996555444444","Адрес",null,BigDecimal.ZERO,null,null,null));
        deliveries.dispatch(delivery.id()); deliveries.markFailed(delivery.id(),new FailDeliveryRequest("Отказ"));
        return deliveries.get(delivery.id());
    }
    @Test void deliveryReturnIsValuedOnDestinationAndRetryIsSafe() {
        stock(home,variant,2,"10001");var sale=sale(home,variant,2,FulfillmentType.CITY_DELIVERY);var delivery=failedDelivery(sale);
        var result=deliveries.returnToWarehouse(delivery.id(),new ReturnDeliveryToWarehouseRequest(office));
        assertThat(deliveries.returnToWarehouse(delivery.id(),new ReturnDeliveryToWarehouseRequest(office))).isEqualTo(result);
        assertThat(restored()).isEqualByComparingTo("10001");
        assertThat(inventory.getBalance(office,variant).onHand()).isEqualTo(2);
        long movements = count("stock_movements");
        assertThatThrownBy(()->returns.create(request(sale,home,1,ReturnCondition.SELLABLE)))
                .isInstanceOfSatisfying(ReturnRuleViolationException.class,
                        e -> assertThat(e.getCode()).isEqualTo("DELIVERY_RETURN_WORKFLOW_REQUIRED"));
        assertThat(count("stock_movements")).isEqualTo(movements);
        assertThat(count("returns")).isZero();
        assertThat(count("return_items")).isZero();consistent();
    }
    @Test void failedDeliveryCustomerReturnIsRejectedBeforeWarehouseReceipt() {
        stock(home,variant,2,"10001");var sale=sale(home,variant,2,FulfillmentType.CITY_DELIVERY);var delivery=failedDelivery(sale);
        long movements=count("stock_movements");
        var before=inventory.getBalance(home,variant);
        assertThatThrownBy(()->returns.create(request(sale,home,1,ReturnCondition.SELLABLE)))
                .isInstanceOfSatisfying(ReturnRuleViolationException.class,
                        e -> assertThat(e.getCode()).isEqualTo("DELIVERY_RETURN_WORKFLOW_REQUIRED"));
        assertThat(count("stock_movements")).isEqualTo(movements);
        assertThat(count("returns")).isZero();
        assertThat(count("return_items")).isZero();
        assertThat(inventory.getBalance(home,variant)).isEqualTo(before);
        assertThat(restored()).isZero();

        // Physical receipt of the entire failed shipment remains available.
        deliveries.returnToWarehouse(delivery.id(),new ReturnDeliveryToWarehouseRequest(home));
        assertThat(restored()).isEqualByComparingTo("10001");
        assertThat(inventory.getBalance(home,variant).onHand()).isEqualTo(2);consistent();
    }
    @Test void sameVariantExchangeCanFulfillReturnedLastUnit() {
        stock(home,variant,1,"5000");var sale=sale(1);payments.create(new CreatePaymentRequest(sale.id(),PaymentMethod.CASH,null,null));sales.fulfill(sale.id());
        var returned=returns.create(request(sale,home,1,ReturnCondition.SELLABLE));
        var request=new CreateExchangeRequest(UUID.randomUUID(),returned.id(),FulfillmentType.SELF_PICKUP,List.of(new CreateSaleItemRequest(variant,home,1,new BigDecimal("10000"))));
        var exchange=exchanges.create(request);assertThat(exchanges.create(request).id()).isEqualTo(exchange.id());
        sales.fulfill(exchange.newSaleId());sales.fulfill(exchange.newSaleId());
        assertThat(restored()).isEqualByComparingTo("5000");
        assertThat(jdbc.queryForObject("select sum(allocated_cost) from inventory_cost_allocations",BigDecimal.class)).isEqualByComparingTo("10000");consistent();
    }
    @Test void partialExchangeToDifferentVariantAndWarehouseKeepsCostsSeparate() {
        stock(home,variant,2,"8000");stock(office,other,1,"7000");var sale=sale(2);
        payments.create(new CreatePaymentRequest(sale.id(),PaymentMethod.CASH,null,null));sales.fulfill(sale.id());
        var returned=returns.create(request(sale,office,1,ReturnCondition.SELLABLE));
        var exchange=exchanges.create(new CreateExchangeRequest(UUID.randomUUID(),returned.id(),FulfillmentType.SELF_PICKUP,List.of(new CreateSaleItemRequest(other,office,1,new BigDecimal("10000")))));
        sales.fulfill(exchange.newSaleId());assertThat(exchange.returnedValue()).isEqualByComparingTo("10000");
        assertThat(restored()).isEqualByComparingTo("4000");
        assertThat(jdbc.queryForObject("select sum(allocated_cost) from inventory_cost_allocations",BigDecimal.class)).isEqualByComparingTo("15000");consistent();
    }
    UUID receipt(long qty,String unitPrice) {
        var purchase=purchases.create(new CreatePurchaseRequest(supplier,List.of(new PurchaseItemRequest(variant,qty,new BigDecimal(unitPrice))),BigDecimal.ZERO,null));
        purchases.confirm(purchase.id());
        return receiving.receive(purchase.id(),new CreatePurchaseReceiptRequest(UUID.randomUUID(),home,List.of(new ReceiptItemRequest(purchase.items().getFirst().id(),qty)),null)).items().getFirst().id();
    }
    @Test void writeOffWithoutOriginUsesFifoAndCannotRepeat() {
        stock(home,variant,2,"10000");stock(home,variant,2,"12000");
        var defect=defects.open(home,variant,null,null,3,"Брак","fifo-test");
        defects.writeOff(defect.id(),"Списано","fifo-test");
        assertThatThrownBy(()->defects.writeOff(defect.id(),"Списано","fifo-test")).isInstanceOf(DefectRuleViolationException.class);
        assertThat(jdbc.queryForObject("select sum(allocated_cost) from inventory_cost_allocations",BigDecimal.class)).isEqualByComparingTo("16000");
        assertThat(inventory.getBalance(home,variant).blocked()).isZero();consistent();
    }
    @Test void writeOffWithReceiptUsesThatLayerRatherThanOldest() {
        receipt(2,"5000");var origin=receipt(2,"6000");
        var defect=defects.open(home,variant,supplier,origin,1,"Брак","fifo-test");defects.writeOff(defect.id(),"Списано","fifo-test");
        assertThat(jdbc.queryForObject("select sum(allocated_cost) from inventory_cost_allocations",BigDecimal.class)).isEqualByComparingTo("6000");
        assertThat(jdbc.queryForObject("select quantity_remaining from inventory_cost_layers where total_cost=10000",Long.class)).isEqualTo(2);consistent();
    }
    @Test void exhaustedSpecifiedLayerCannotSilentlyUseAnotherAndUnblockRollsBack() {
        var origin=receipt(1,"5000");var sale=sale(1);sales.fulfill(sale.id());receipt(2,"6000");
        var defect=defects.open(home,variant,supplier,origin,1,"Брак","fifo-test");
        assertThatThrownBy(()->defects.writeOff(defect.id(),"Списано","fifo-test")).isInstanceOf(InventoryCostException.class);
        assertThat(inventory.getBalance(home,variant).blocked()).isEqualTo(1);
        assertThat(count("inventory_cost_write_offs")).isZero();consistent();
    }
    RecordStockMovement out(UUID id,long qty) { return new RecordStockMovement(id,home,variant,StockMovementType.SALE_OUT,qty,"TEST",id,"fifo-test"); }
    @Test void postSaleOutReplayReturnsOriginalCostAndChangedPayloadIsRejected() {
        stock(home,variant,1,"123.45");UUID id=UUID.randomUUID();var command=out(id,1);
        assertThat(posting.postSaleOut(command)).isEqualByComparingTo("123.45");
        assertThat(posting.postSaleOut(command)).isEqualByComparingTo("123.45");
        assertThatThrownBy(()->posting.postSaleOut(out(id,2))).isInstanceOf(InventoryOperationConflictException.class);
        assertThat(count("inventory_cost_allocations")).isEqualTo(1);consistent();
    }
    @Test void writeOffReplayRejectsChangedOriginEvenWhenChangedToNull() {
        var origin=receipt(1,"5000");UUID id=UUID.randomUUID();
        var command=new RecordStockMovement(id,home,variant,StockMovementType.WRITE_OFF,1,"TEST",id,"fifo-test");
        assertThat(posting.postWriteOff(command,origin)).isEqualByComparingTo("5000");
        assertThat(posting.postWriteOff(command,origin)).isEqualByComparingTo("5000");
        assertThatThrownBy(()->posting.postWriteOff(command,null)).isInstanceOf(InventoryCostException.class);consistent();
    }
    @Test void insufficientValuationRollsBackPhysicalSaleAndAudit() {
        inventory.recordMovement(new RecordStockMovement(UUID.randomUUID(),home,variant,StockMovementType.ADJUSTMENT_IN,1,"LEGACY",UUID.randomUUID(),"fifo-test"));
        long audit=count("audit_entries");
        assertThatThrownBy(()->posting.postSaleOut(out(UUID.randomUUID(),1))).isInstanceOf(InventoryCostException.class);
        assertThat(inventory.getBalance(home,variant).onHand()).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(1);assertThat(count("audit_entries")).isEqualTo(audit);
        assertThat(count("inventory_unvalued_movements")).isEqualTo(1);
    }
    @Test void historicalUnvaluedReturnIsDetectedRatherThanInventingCost() {
        stock(home,variant,2,"10000");var sale=sale(2);sales.fulfill(sale.id());
        UUID returnId=UUID.randomUUID(),item=UUID.randomUUID();
        jdbc.update("insert into returns(id,sale_id,warehouse_id,idempotency_key,request_fingerprint,reason,created_by,created_at) values (?,?,?,? ,?,'Legacy','fifo-test',now())",returnId,sale.id(),home,UUID.randomUUID(),"a".repeat(64));
        jdbc.update("insert into return_items(id,return_id,sale_item_id,quantity,condition) values (?,?,?,1,'SELLABLE')",item,returnId,sale.items().getFirst().id());
        inventory.recordMovement(new RecordStockMovement(item,home,variant,StockMovementType.RETURN_IN,1,"SALE_RETURN",returnId,"fifo-test"));
        assertThatThrownBy(()->returns.create(request(sale,home,1,ReturnCondition.SELLABLE))).isInstanceOfSatisfying(InventoryCostException.class,e->assertThat(e.getCode()).isEqualTo("LEGACY_RETURN_COST_MISSING"));
        assertThat(restored()).isZero();assertThat(count("inventory_unvalued_movements")).isEqualTo(1);
    }
    @Test void failureAfterRestorationRowsRollsBackReturnStockCostAndAudit() {
        stock(home,variant,1,"50");var sale=sale(1);sales.fulfill(sale.id());long audit=count("audit_entries");
        jdbc.execute("create function test_fail_cost_layer() returns trigger language plpgsql as $$ begin raise exception 'injected'; end; $$");
        jdbc.execute("create trigger test_fail_cost_layer before insert on inventory_cost_layers for each row execute function test_fail_cost_layer()");
        try {
            assertThatThrownBy(()->returns.create(request(sale,home,1,ReturnCondition.BLOCKED))).isInstanceOf(DataAccessException.class);
            assertThat(count("returns")).isZero();assertThat(count("inventory_cost_restorations")).isZero();
            assertThat(count("stock_movements")).isEqualTo(2);assertThat(count("audit_entries")).isEqualTo(audit);
            assertThat(inventory.getBalance(home,variant).onHand()).isZero();consistent();
        } finally { jdbc.execute("drop trigger test_fail_cost_layer on inventory_cost_layers");jdbc.execute("drop function test_fail_cost_layer()"); }
    }
    @Test void historyAndLayerOriginsCannotBeRewritten() {
        stock(home,variant,1,"50");var sale=sale(1);sales.fulfill(sale.id());returns.create(request(sale,home,1,ReturnCondition.SELLABLE));
        for(String sql:List.of("update inventory_cost_allocations set allocated_cost=0","delete from inventory_cost_restorations","update inventory_cost_layers set total_cost=total_cost+1","delete from inventory_cost_movements")) {
            assertThatThrownBy(()->jdbc.update(sql)).isInstanceOf(DataAccessException.class);
        }
        consistent();
    }
    @Test void parallelDuplicatePostingConsumesOnlyOnce() throws Exception {
        stock(home,variant,1,"55");var command=out(UUID.randomUUID(),1);
        var results=parallel(()->posting.postSaleOut(command),()->posting.postSaleOut(command));
        assertThat(results).allSatisfy(cost->assertThat(cost).isEqualByComparingTo("55"));
        assertThat(count("inventory_cost_allocations")).isEqualTo(1);consistent();
    }
    @Test void parallelDistinctReturnsCannotRestoreSameUnitTwice() throws Exception {
        stock(home,variant,1,"55");var sale=sale(1);sales.fulfill(sale.id());
        Callable<Boolean> action=()->{try {returns.create(request(sale,home,1,ReturnCondition.SELLABLE));return true;}catch(kg.chairx.returning.application.ReturnRuleViolationException e){return false;}};
        assertThat(parallel(action,action)).containsExactlyInAnyOrder(true,false);
        assertThat(restored()).isEqualByComparingTo("55");consistent();
    }
    @Test void parallelFailedDeliveryAndCustomerReturnNeverCreditUnreceivedGoods() throws Exception {
        stock(home,variant,1,"55");var sale=sale(home,variant,1,FulfillmentType.CITY_DELIVERY);var delivery=failedDelivery(sale);
        Callable<Boolean> customerReturn=()->{try{returns.create(request(sale,office,1,ReturnCondition.SELLABLE));return true;}catch(ReturnRuleViolationException e){
            assertThat(e.getCode()).isEqualTo("DELIVERY_RETURN_WORKFLOW_REQUIRED");return false;
        }};
        Callable<Boolean> deliveryReturn=()->{deliveries.returnToWarehouse(delivery.id(),new ReturnDeliveryToWarehouseRequest(home));return true;};
        assertThat(parallel(customerReturn,deliveryReturn)).containsExactlyInAnyOrder(true,false);
        assertThat(count("returns")).isZero();
        assertThat(count("return_items")).isZero();
        assertThat(inventory.getBalance(office,variant).onHand()).isZero();
        assertThat(inventory.getBalance(home,variant).onHand()).isEqualTo(1);
        assertThat(restored()).isEqualByComparingTo("55");consistent();
    }
    @Test void dispatchRacingCustomerReturnNeverRestoresUnreceivedGoods() throws Exception {
        stock(home,variant,1,"55");
        var sale=sale(home,variant,1,FulfillmentType.CITY_DELIVERY);
        var delivery=deliveries.create(new CreateDeliveryRequest(
                sale.id(),"Клиент","+996555444444","Адрес",null,BigDecimal.ZERO,null,null,null));
        Callable<Boolean> customerReturn=()->{
            try {returns.create(request(sale,home,1,ReturnCondition.SELLABLE));return true;}
            catch(ReturnRuleViolationException e) {
                assertThat(e.getCode()).isEqualTo("DELIVERY_NOT_COMPLETED");return false;
            }
        };
        Callable<Boolean> dispatch=()->{deliveries.dispatch(delivery.id());return true;};
        assertThat(parallel(customerReturn,dispatch)).containsExactlyInAnyOrder(false,true);
        assertThat(deliveries.get(delivery.id()).status())
                .isEqualTo(kg.chairx.delivery.domain.DeliveryStatus.IN_TRANSIT);
        assertThat(count("returns")).isZero();
        assertThat(count("return_items")).isZero();
        assertThat(inventory.getBalance(home,variant).onHand()).isZero();
        assertThat(restored()).isZero();consistent();
    }

    @Test void deliveryCompletionRacingCustomerReturnOnlyCreditsDeliveredGoods() throws Exception {
        stock(home,variant,1,"55");
        var sale=sale(home,variant,1,FulfillmentType.CITY_DELIVERY);
        var delivery=deliveries.create(new CreateDeliveryRequest(
                sale.id(),"Клиент","+996555444444","Адрес",null,BigDecimal.ZERO,null,null,null));
        deliveries.dispatch(delivery.id());
        Callable<Boolean> customerReturn=()->{
            try {returns.create(request(sale,home,1,ReturnCondition.SELLABLE));return true;}
            catch(ReturnRuleViolationException e) {
                assertThat(e.getCode()).isEqualTo("DELIVERY_NOT_COMPLETED");return false;
            }
        };
        Callable<Boolean> complete=()->{deliveries.markDelivered(delivery.id());return true;};
        var results=parallel(customerReturn,complete);
        assertThat(results).contains(true);
        assertThat(deliveries.get(delivery.id()).status())
                .isEqualTo(kg.chairx.delivery.domain.DeliveryStatus.DELIVERED);
        long accepted=count("returns");
        assertThat(accepted).isBetween(0L,1L);
        assertThat(count("return_items")).isEqualTo(accepted);
        assertThat(inventory.getBalance(home,variant).onHand()).isEqualTo(accepted);
        assertThat(restored()).isEqualByComparingTo(
                BigDecimal.valueOf(55).multiply(BigDecimal.valueOf(accepted)));
        consistent();
    }

    <T> List<T> parallel(Callable<T> left,Callable<T> right) throws Exception {
        var barrier=new CyclicBarrier(2);var executor=Executors.newFixedThreadPool(2);
        Callable<T> first=()->{authenticate();try{barrier.await(5,TimeUnit.SECONDS);return left.call();}finally{SecurityContextHolder.clearContext();}};
        Callable<T> second=()->{authenticate();try{barrier.await(5,TimeUnit.SECONDS);return right.call();}finally{SecurityContextHolder.clearContext();}};
        var a=executor.submit(first);var b=executor.submit(second);
        try{return List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));}
        finally{a.cancel(true);b.cancel(true);executor.shutdownNow();}
    }
    void authenticate(){SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("fifo-test",null,List.of()));}
}
