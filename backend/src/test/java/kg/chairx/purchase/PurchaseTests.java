package kg.chairx.purchase;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.domain.StockQuantityLimitException;
import kg.chairx.operations.ReceivePurchase;
import kg.chairx.purchase.api.*;
import kg.chairx.purchase.application.PurchaseRuleViolationException;
import kg.chairx.purchase.application.PurchaseService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static kg.chairx.inventory.domain.StockMovementType.*;
import static kg.chairx.purchase.domain.PurchaseStatus.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@WithMockUser(username = "purchase-tester")
@Timeout(30)
class PurchaseTests {
    @Autowired PurchaseService purchases;
    @Autowired ReceivePurchase receiving;
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    UUID supplier, product, variant, otherVariant, warehouse;

    @BeforeEach
    void fixture() {
        reset();
        supplier = UUID.randomUUID(); product = UUID.randomUUID();
        // Ordering guarantees that the rollback test fails on the second stock operation.
        variant = UUID.fromString("00000000-0000-0000-0000-000000000001");
        otherVariant = UUID.fromString("00000000-0000-0000-0000-000000000002");
        warehouse = jdbc.queryForObject("select id from warehouses where code='HOME'", UUID.class);
        jdbc.update("insert into suppliers(id,name,active,created_at,updated_at) values (?,'Purchase fixture',true,now(),now())", supplier);
        jdbc.update("insert into products(id,name,active,created_at,updated_at) values (?,'Purchase fixture',true,now(),now())", product);
        for (UUID id : List.of(variant, otherVariant)) jdbc.update("""
                insert into product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at)
                values (?,?,'Purchase variant',100,true,now(),now())
                """, id, product);
    }
    @AfterEach
    void cleanup() {
        reset();
        jdbc.update("delete from product_variants where product_id=?", product);
        jdbc.update("delete from products where id=?", product);
        jdbc.update("delete from suppliers where id=?", supplier);
    }
    private void reset() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("chairx_test");
        // Testcontainer only: posted receipts and the stock journal prohibit ordinary DELETE.
        jdbc.execute("""
        truncate defects,
                 purchase_receipt_items,
                 purchase_receipts,
                 purchase_items,
                 purchases,
                 stock_movements,
                 inventory_balances
        """);
        jdbc.update("delete from audit_entries where entity_type in ('PURCHASE','PURCHASE_RECEIPT','STOCK_MOVEMENT')");
    }
    @Test
    void createsAndUpdatesDraftThroughApi() throws Exception {
        var request = new CreatePurchaseRequest(supplier, List.of(line(variant,100,"10.00")), null," Draft ");
        var response = mvc.perform(post("/api/purchases").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.comment").value("Draft"))
                .andReturn().getResponse();
        UUID id = UUID.fromString(mapper.readTree(response.getContentAsString()).get("id").asText());
        assertThat(response.getHeader("Location")).isEqualTo("/api/purchases/"+id);
        var original = purchases.get(id);
        assertThat(original.items().getFirst().orderedQuantity()).isEqualTo(100);
        assertThat(original.items().getFirst().purchaseUnitCost()).isEqualByComparingTo("10.00");
        assertThat(original.items().getFirst().finalUnitCost()).isNull();
        var update = new UpdatePurchaseRequest(supplier,List.of(line(otherVariant,25,"12.00")),money("5.00")," Updated ");
        mvc.perform(put("/api/purchases/"+id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(update))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        var saved = purchases.get(id);
        assertThat(saved.createdAt()).isEqualTo(original.createdAt());
        assertThat(saved.comment()).isEqualTo("Updated");
        assertThat(saved.items()).hasSize(1);
        var item = saved.items().getFirst();
        assertThat(item.productVariantId()).isEqualTo(otherVariant);
        assertThat(item.orderedQuantity()).isEqualTo(25);
        assertThat(item.receivedQuantity()).isZero();
        assertThat(item.purchaseUnitCost()).isEqualByComparingTo("12.00");
        assertThat(item.allocatedCargoCost()).isEqualByComparingTo("5.00");
        assertThat(item.finalUnitCost()).isEqualByComparingTo("12.20");
        assertThat(count("purchase_items")).isEqualTo(1);
        assertThat(count("stock_movements")).isZero();
    }
    @Test
    void confirmationPreservesTermsLocksDraftEditsAndDoesNotChangeInventory() {
        inventory.recordMovement(new RecordStockMovement(UUID.randomUUID(),warehouse,variant,ADJUSTMENT_IN,7,"TEST",UUID.randomUUID(),"purchase-tester"));
        var draft = draft("12.34"); var before = inventory.getBalance(warehouse,variant);
        var confirmed = purchases.confirm(draft.id());
        assertThat(confirmed.status()).isEqualTo(CONFIRMED);
        assertThat(confirmed.confirmedAt()).isNotNull();
        assertThat(confirmed.items()).isEqualTo(draft.items());
        assertThat(confirmed.cargoCost()).isEqualByComparingTo("12.34");
        assertThat(purchases.confirm(draft.id())).isEqualTo(confirmed);
        rule("INVALID_PURCHASE_STATUS", () -> purchases.update(draft.id(),new UpdatePurchaseRequest(supplier,
                List.of(line(variant,200,"1.00")),money("99.00"),null)));
        assertThat(purchases.get(draft.id())).isEqualTo(confirmed);
        assertThat(inventory.getBalance(warehouse,variant)).isEqualTo(before);
        assertThat(count("stock_movements")).isEqualTo(1);
        assertThat(count("purchase_receipts")).isZero();
    }
    @Test
    void cargoCanBeFinalizedAfterConfirmationButLocksAtFirstReceipt() {
        var p = purchases.confirm(draft(null).id());
        rule("CARGO_COST_REQUIRED", () -> receiving.receive(p.id(),receipt(p,1)));
        assertThat(count("purchase_receipts")).isZero();
        var priced = purchases.setCargo(p.id(),new SetCargoCostRequest(money("10.00")));
        assertThat(priced.costsLockedAt()).isNull();
        assertThat(priced.items().getFirst().finalUnitCost()).isEqualByComparingTo("10.10");
        receiving.receive(p.id(),receipt(p,1)); var locked = purchases.get(p.id());
        assertThat(locked.costsLockedAt()).isNotNull();
        rule("INVALID_PURCHASE_STATUS", () -> purchases.setCargo(p.id(),new SetCargoCostRequest(money("20.00"))));
        rule("INVALID_PURCHASE_STATUS", () -> purchases.update(p.id(),new UpdatePurchaseRequest(supplier,
                List.of(line(variant,200,"1.00")),money("20.00"),null)));
        assertThat(purchases.get(p.id())).isEqualTo(locked);
    }
    @Test
    void byQuantityIgnoresPricesAndConservesCargoCents() {
        var p = purchases.create(new CreatePurchaseRequest(supplier,List.of(line(variant,1,"100.00"),line(otherVariant,2,"1.00")),money("10.00"),null));
        assertThat(p.cargoAllocationMethod()).isEqualTo("BY_QUANTITY");
        assertThat(p.items().get(0).allocatedCargoCost()).isEqualByComparingTo("3.33");
        assertThat(p.items().get(1).allocatedCargoCost()).isEqualByComparingTo("6.67");
        assertThat(p.items().get(0).finalUnitCost()).isEqualByComparingTo("103.33");
        assertThat(p.items().get(1).finalUnitCost()).isEqualByComparingTo("4.335");
        assertThat(p.items().stream().map(PurchaseItemResponse::allocatedCargoCost).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("10.00");
    }
    @Test
    void hundredUnitsArriveAsSixtyThenFortyWithCostsAndStatusTransitions() {
        var p = confirmed(); assertThat(p.status()).isEqualTo(CONFIRMED);
        var first = receiving.receive(p.id(),receipt(p,60)); var partial = purchases.get(p.id());
        assertThat(partial.status()).isEqualTo(PARTIALLY_RECEIVED);
        assertThat(partial.items().getFirst().receivedQuantity()).isEqualTo(60);
        assertThat(inventory.getBalance(warehouse,variant).onHand()).isEqualTo(60);
        assertThat(first.items().getFirst().allocatedCargoCost()).isEqualByComparingTo("6.00");
        assertThat(first.items().getFirst().totalCost()).isEqualByComparingTo("606.00");
        var last = receiving.receive(p.id(),receipt(p,40)); var complete = purchases.get(p.id());
        assertThat(complete.status()).isEqualTo(RECEIVED);
        assertThat(complete.items().getFirst().receivedQuantity()).isEqualTo(100);
        assertThat(complete.costsLockedAt()).isEqualTo(partial.costsLockedAt());
        assertThat(last.items().getFirst().allocatedCargoCost()).isEqualByComparingTo("4.00");
        assertThat(last.items().getFirst().totalCost()).isEqualByComparingTo("404.00");
        assertThat(inventory.getBalance(warehouse,variant).onHand()).isEqualTo(100);
        assertThat(count("purchase_receipts")).isEqualTo(2); assertThat(count("stock_movements")).isEqualTo(2);
    }
    @Test
    void splitReceiptsConserveRoundingRemainder() {
        var p = purchases.confirm(purchases.create(new CreatePurchaseRequest(supplier,List.of(line(variant,3,"2.00")),money("0.01"),null)).id());
        var first = receiving.receive(p.id(),receipt(p,1)); var second = receiving.receive(p.id(),receipt(p,1)); var third = receiving.receive(p.id(),receipt(p,1));
        assertThat(first.items().getFirst().allocatedCargoCost()).isEqualByComparingTo("0.00");
        assertThat(second.items().getFirst().allocatedCargoCost()).isEqualByComparingTo("0.01");
        assertThat(third.items().getFirst().allocatedCargoCost()).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("select sum(total_cost) from purchase_receipt_items",BigDecimal.class)).isEqualByComparingTo("6.01");
    }
    @Test
    void receiptAboveRemainderIsRejectedThroughApiWithoutSideEffects() throws Exception {
        var p = confirmed(); receiving.receive(p.id(),receipt(p,60)); var before = purchases.get(p.id()); int audits = count("audit_entries");
        mvc.perform(post("/api/purchases/"+p.id()+"/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(receipt(p,41)))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_QUANTITY_EXCEEDED"));
        assertThat(purchases.get(p.id())).isEqualTo(before);
        assertThat(count("purchase_receipts")).isEqualTo(1); assertThat(count("stock_movements")).isEqualTo(1);
        assertThat(count("audit_entries")).isEqualTo(audits); assertThat(inventory.getBalance(warehouse,variant).onHand()).isEqualTo(60);
    }
    @Test
    void receiptCannotUseItemFromAnotherPurchase() {
        var own = confirmed(); var foreign = confirmed();
        rule("INVALID_RECEIPT_ITEM", () -> receiving.receive(own.id(),receipt(foreign,1)));
        assertThat(purchases.get(own.id())).isEqualTo(own); assertThat(purchases.get(foreign.id())).isEqualTo(foreign);
        assertThat(count("purchase_receipts")).isZero(); assertThat(count("stock_movements")).isZero();
    }
    @Test
    void duplicatePurchaseItemInReceiptIsRejectedAtomically() {
        var p = confirmed(); var line = new ReceiptItemRequest(p.items().getFirst().id(),30);
        rule("DUPLICATE_RECEIPT_ITEM", () -> receiving.receive(p.id(),new CreatePurchaseReceiptRequest(UUID.randomUUID(),warehouse,List.of(line,line),null)));
        assertThat(purchases.get(p.id())).isEqualTo(p); assertThat(count("purchase_receipts")).isZero(); assertThat(count("stock_movements")).isZero();
    }
    @Test
    void receiptPostsOneCorrectPurchaseInPerLine() {
        var p = purchases.confirm(purchases.create(new CreatePurchaseRequest(supplier,List.of(line(variant,3,"2.00"),line(otherVariant,5,"4.00")),money("8.00"),null)).id());
        var r = receiving.receive(p.id(),new CreatePurchaseReceiptRequest(UUID.randomUUID(),warehouse,
                p.items().stream().map(i -> new ReceiptItemRequest(i.id(),i.orderedQuantity())).toList(),"delivery"));
        for (var item : r.items()) {
            var movements = inventory.listMovements(warehouse,item.productVariantId(),0,20).items(); assertThat(movements).hasSize(1);
            var m = movements.getFirst();
            assertThat(m.type()).isEqualTo(PURCHASE_IN); assertThat(m.quantity()).isEqualTo(item.quantity());
            assertThat(m.warehouseId()).isEqualTo(warehouse); assertThat(m.productVariantId()).isEqualTo(item.productVariantId());
            assertThat(m.operationId()).isEqualTo(item.id()); assertThat(m.sourceType()).isEqualTo("PURCHASE_RECEIPT");
            assertThat(m.sourceId()).isEqualTo(r.id()); assertThat(m.actor()).isEqualTo("purchase-tester");
            var b = inventory.getBalance(warehouse,item.productVariantId());
            assertThat(b.onHand()).isEqualTo(item.quantity()); assertThat(b.reserved()).isZero(); assertThat(b.blocked()).isZero();
        }
        assertThat(count("stock_movements")).isEqualTo(2);
    }
    @Test
    void identicalRetryReturnsOriginalReceiptWithoutNewMovementOrAudit() {
        var p = confirmed(); var request = receipt(p,60); var first = receiving.receive(p.id(),request); int audits = count("audit_entries");
        assertThat(receiving.receive(p.id(),request)).isEqualTo(first);
        assertThat(count("purchase_receipts")).isEqualTo(1); assertThat(count("purchase_receipt_items")).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(1); assertThat(count("audit_entries")).isEqualTo(audits);
        assertThat(purchases.get(p.id()).items().getFirst().receivedQuantity()).isEqualTo(60);
        assertThat(inventory.getBalance(warehouse,variant).onHand()).isEqualTo(60);
    }
    @Test
    void sameKeyWithChangedPayloadIsRejected() {
        var p = confirmed(); var request = receipt(p,60); receiving.receive(p.id(),request); var before = purchases.get(p.id());
        rule("RECEIPT_IDEMPOTENCY_CONFLICT", () -> receiving.receive(p.id(),new CreatePurchaseReceiptRequest(request.idempotencyKey(),warehouse,
                List.of(new ReceiptItemRequest(p.items().getFirst().id(),40)),null)));
        assertThat(purchases.get(p.id())).isEqualTo(before); assertThat(count("purchase_receipts")).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(1); assertThat(inventory.getBalance(warehouse,variant).onHand()).isEqualTo(60);
    }
    @Test
    void finalReceiptRetryAfterReceivedReturnsOriginalResult() {
        var p = confirmed(); receiving.receive(p.id(),receipt(p,60)); var request = receipt(p,40); var last = receiving.receive(p.id(),request);
        var complete = purchases.get(p.id()); assertThat(complete.status()).isEqualTo(RECEIVED); int audits = count("audit_entries");
        assertThat(receiving.receive(p.id(),request)).isEqualTo(last); assertThat(purchases.get(p.id())).isEqualTo(complete);
        assertThat(count("purchase_receipts")).isEqualTo(2); assertThat(count("stock_movements")).isEqualTo(2);
        assertThat(count("audit_entries")).isEqualTo(audits); assertThat(inventory.getBalance(warehouse,variant).onHand()).isEqualTo(100);
    }
    @Test
    void inventoryFailureOnSecondLineRollsBackEntireReceiptStatusAndAudit() {
        inventory.recordMovement(new RecordStockMovement(UUID.randomUUID(),warehouse,otherVariant,ADJUSTMENT_IN,Long.MAX_VALUE,"TEST",UUID.randomUUID(),"purchase-tester"));
        var p = purchases.confirm(purchases.create(new CreatePurchaseRequest(supplier,List.of(line(variant,1,"2.00"),line(otherVariant,1,"2.00")),money("1.00"),null)).id());
        int audits = count("audit_entries");
        assertThatThrownBy(() -> receiving.receive(p.id(),new CreatePurchaseReceiptRequest(UUID.randomUUID(),warehouse,
                p.items().stream().map(i -> new ReceiptItemRequest(i.id(),1)).toList(),null))).isInstanceOf(StockQuantityLimitException.class);
        assertThat(purchases.get(p.id())).isEqualTo(p); assertThat(count("purchase_receipts")).isZero(); assertThat(count("purchase_receipt_items")).isZero();
        assertThat(count("stock_movements")).isEqualTo(1); assertThat(count("inventory_balances")).isEqualTo(1); assertThat(count("audit_entries")).isEqualTo(audits);
        assertThat(inventory.getBalance(warehouse,variant).onHand()).isZero();
        assertThat(inventory.getBalance(warehouse,otherVariant).onHand()).isEqualTo(Long.MAX_VALUE);
    }
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void cancellationIsAllowedBeforeAnyReceipt(boolean confirm) {
        var p = draft("10.00"); if (confirm) purchases.confirm(p.id()); var cancelled = purchases.cancel(p.id());
        assertThat(cancelled.status()).isEqualTo(CANCELLED); assertThat(purchases.cancel(p.id())).isEqualTo(cancelled);
        rule("INVALID_PURCHASE_STATUS", () -> receiving.receive(p.id(),receipt(p,1)));
        assertThat(count("purchase_receipts")).isZero(); assertThat(count("stock_movements")).isZero();
    }
    @ParameterizedTest @ValueSource(longs = {60,100})
    void cancellationIsRejectedAfterPartialOrFullReceipt(long quantity) {
        var p = confirmed(); receiving.receive(p.id(),receipt(p,quantity)); var before = purchases.get(p.id());
        rule("INVALID_PURCHASE_STATUS", () -> purchases.cancel(p.id())); assertThat(purchases.get(p.id())).isEqualTo(before);
        assertThat(count("purchase_receipts")).isEqualTo(1); assertThat(inventory.getBalance(warehouse,variant).onHand()).isEqualTo(quantity);
    }
    @Test
    void parallelReceiptsWaitForPostgresLockAndCannotExceedOrderedQuantity() throws Exception {
        var p = confirmed(); var firstPosted = new CountDownLatch(1); var releaseCommit = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> worker(() -> {
                receiving.receive(p.id(),receipt(p,60)); firstPosted.countDown();
                try { if (!releaseCommit.await(8,TimeUnit.SECONDS)) throw new AssertionError("Commit gate timed out after 8s"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                return "POSTED";
            }));
            assertThat(firstPosted.await(8,TimeUnit.SECONDS)).as("First receipt reaches commit gate").isTrue();
            var second = executor.submit(() -> {
                try { return worker(() -> {
                    jdbc.execute("set local application_name='purchase_receipt_race'");
                    receiving.receive(p.id(),receipt(p,60)); return "POSTED";
                }); } catch (PurchaseRuleViolationException e) { return e.getCode(); }
            });
            // Prove actual PostgreSQL contention rather than assuming the threads overlap.
            long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(3); boolean waiting = false;
            while (System.nanoTime()<deadline) {
                waiting = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where application_name='purchase_receipt_race' and wait_event_type='Lock')",Boolean.class));
                if (waiting) break; Thread.sleep(20);
            }
            assertThat(waiting).as("Second receipt waits on PostgreSQL row lock").isTrue(); releaseCommit.countDown();
            assertThat(first.get(8,TimeUnit.SECONDS)).isEqualTo("POSTED");
            assertThat(second.get(8,TimeUnit.SECONDS)).isEqualTo("PURCHASE_QUANTITY_EXCEEDED");
        } finally {
            releaseCommit.countDown(); executor.shutdownNow();
            assertThat(executor.awaitTermination(6,TimeUnit.SECONDS)).as("Workers terminate within 6s").isTrue();
        }
        var result = purchases.get(p.id()); assertThat(result.status()).isEqualTo(PARTIALLY_RECEIVED);
        assertThat(result.items().getFirst().receivedQuantity()).isEqualTo(60); assertThat(count("purchase_receipts")).isEqualTo(1);
        assertThat(count("purchase_receipt_items")).isEqualTo(1); assertThat(count("stock_movements")).isEqualTo(1);
        assertThat(inventory.getBalance(warehouse,variant).onHand()).isEqualTo(60);
    }
    private String worker(java.util.function.Supplier<String> work) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("purchase-tester","unused",List.of()));
        SecurityContextHolder.setContext(context);
        try {
            var tx = new TransactionTemplate(transactions); tx.setTimeout(12);
            return tx.execute(status -> { jdbc.execute("set local lock_timeout='5s'"); jdbc.execute("set local statement_timeout='6s'"); return work.get(); });
        } finally { SecurityContextHolder.clearContext(); }
    }
    private PurchaseResponse draft(String cargo) { return purchases.create(new CreatePurchaseRequest(supplier,List.of(line(variant,100,"10.00")),cargo==null?null:money(cargo),null)); }
    private PurchaseResponse confirmed() { return purchases.confirm(draft("10.00").id()); }
    private PurchaseItemRequest line(UUID id,long quantity,String price) { return new PurchaseItemRequest(id,quantity,money(price)); }
    private CreatePurchaseReceiptRequest receipt(PurchaseResponse p,long quantity) { return new CreatePurchaseReceiptRequest(UUID.randomUUID(),warehouse,List.of(new ReceiptItemRequest(p.items().getFirst().id(),quantity)),null); }
    private BigDecimal money(String value) { return new BigDecimal(value); }
    private int count(String table) { return jdbc.queryForObject("select count(*) from "+table,Integer.class); }
    private void rule(String code,org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(PurchaseRuleViolationException.class,e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
