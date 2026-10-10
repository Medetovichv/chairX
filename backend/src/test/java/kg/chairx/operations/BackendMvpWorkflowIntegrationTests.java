package kg.chairx.operations;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.purchase.api.*;
import kg.chairx.purchase.application.*;
import kg.chairx.purchase.domain.*;
import kg.chairx.sale.api.*;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.finance.application.CashFlowService;
import kg.chairx.finance.domain.FinanceAccount;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(kg.chairx.FundedFinanceExtension.class)
@SpringBootTest(properties={
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"})
@Import(PostgresTestConfiguration.class)
class BackendMvpWorkflowIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PurchaseService purchases;
    @Autowired PurchasePaymentService purchasePayments;
    @Autowired ReceivePurchase receipts;
    @Autowired SaleService sales;
    @Autowired PaymentService salePayments;
    @Autowired InventoryService inventory;
    @Autowired CashFlowService cashFlow;

    UUID supplier,product,variant,customer,home,office;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("select current_database()",String.class))
                .isEqualTo("chairx_test");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("p20-workflow",null,List.of()));
        clear();
        jdbc.update("update finance_accounts set balance=100000,opening_balance_initialized=true where code='CASH'");
        jdbc.update("update finance_accounts set balance=200000,opening_balance_initialized=true where code='BANK'");
        home=jdbc.queryForObject("select id from warehouses where code='HOME'",UUID.class);
        office=jdbc.queryForObject("select id from warehouses where code='OFFICE'",UUID.class);
        supplier=UUID.randomUUID();product=UUID.randomUUID();
        variant=UUID.randomUUID();customer=UUID.randomUUID();
        jdbc.update("insert into suppliers(id,name,active,created_at,updated_at) values (?,'P20 Supply',true,now(),now())",supplier);
        jdbc.update("insert into products(id,name,active,created_at,updated_at) values (?,'P20 Chair',true,now(),now())",product);
        jdbc.update("""
                insert into product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at)
                values (?,?,'Blue',8500,true,now(),now())
                """,variant,product);
        jdbc.update("""
                insert into customers(id,full_name,phone,active,created_at,updated_at)
                values(?,'P20 Customer','+996555111999',true,now(),now())
                """,customer);
    }

    @AfterEach void cleanup() {
        clear();
        jdbc.update("delete from customers where id=?",customer);
        jdbc.update("delete from product_variants where id=?",variant);
        jdbc.update("delete from products where id=?",product);
        jdbc.update("delete from suppliers where id=?",supplier);
        SecurityContextHolder.clearContext();
    }

    @Test void purchasePartialReceiptsAndMultiWarehouseSaleKeepMoneyAndFifoSeparate() {
        var zone=ZoneId.of("Asia/Bishkek");
        var today=LocalDate.now(zone);
        var from=today.atStartOfDay(zone).toInstant();
        var to=today.plusDays(1).atStartOfDay(zone).toInstant();
        var baseline=cashFlow.summary(from,to);

        var purchase=purchases.confirm(purchases.create(new CreatePurchaseRequest(
                supplier,List.of(new PurchaseItemRequest(
                        variant,20,new BigDecimal("5000"))),
                new BigDecimal("20000"),"P20 purchasing")).id());
        var advance=purchasePayments.create(purchase.id(),supplierPayment(50000));
        assertThat(purchasePayments.create(purchase.id(),
                new CreatePurchasePaymentRequest(advance.idempotencyKey(),
                        PurchasePaymentKind.SUPPLIER,FinanceAccount.BANK,
                        new BigDecimal("50000"),null,null)).id()).isEqualTo(advance.id());

        var first=receipts.receive(purchase.id(),receipt(purchase,home,10));
        assertThat(first.items()).hasSize(1);
        assertThat(inventory.getBalance(home,variant).onHand()).isEqualTo(10);
        assertThat(inventory.getBalance(office,variant).onHand()).isZero();
        assertThat(purchases.get(purchase.id()).status()).isEqualTo(PurchaseStatus.PARTIALLY_RECEIVED);

        var second=receipts.receive(purchase.id(),receipt(purchase,office,10));
        assertThat(second.items()).hasSize(1);
        assertThat(purchases.get(purchase.id()).status()).isEqualTo(PurchaseStatus.RECEIVED);
        assertThat(inventory.getBalance(office,variant).onHand()).isEqualTo(10);

        assertThatThrownBy(()->receipts.receive(purchase.id(),receipt(purchase,home,1)))
                .isInstanceOf(PurchaseRuleViolationException.class);

        purchasePayments.create(purchase.id(),supplierPayment(50000));
        purchasePayments.create(purchase.id(),new CreatePurchasePaymentRequest(
                UUID.randomUUID(),PurchasePaymentKind.CARGO,FinanceAccount.CASH,
                new BigDecimal("20000"),null,null));
        var paid=purchasePayments.list(purchase.id());
        assertThat(paid.remainingSupplier()).isEqualByComparingTo("0");
        assertThat(paid.remainingCargo()).isEqualByComparingTo("0");
        assertThat(balance("BANK")).isEqualByComparingTo("100000");
        assertThat(balance("CASH")).isEqualByComparingTo("80000");
        assertThat(jdbc.queryForObject(
                "select coalesce(sum(total_cost),0) from purchase_receipt_items",
                BigDecimal.class)).isEqualByComparingTo("120000");

        var sale=sales.create(new CreateSaleRequest(
                UUID.randomUUID(),customer,FulfillmentType.SELF_PICKUP,
                List.of(new CreateSaleItemRequest(variant,home,2,new BigDecimal("8500")),
                        new CreateSaleItemRequest(variant,office,3,new BigDecimal("8500")))));
        var payment=salePayments.create(new CreatePaymentRequest(
                sale.id(),PaymentMethod.TRANSFER,"MBANK-20",null,
                kg.chairx.payment.domain.PaymentChannel.MBANK));
        sales.fulfill(sale.id());
        assertThat(payment.amount()).isEqualByComparingTo("42500");
        assertThat(inventory.getBalance(home,variant).onHand()).isEqualTo(8);
        assertThat(inventory.getBalance(office,variant).onHand()).isEqualTo(7);
        assertThat(jdbc.queryForObject("""
                select coalesce(sum(allocated_cost),0)
                from inventory_cost_allocations
                """,BigDecimal.class)).isEqualByComparingTo("30000");
        assertThat(balance("BANK")).isEqualByComparingTo("142500");
        assertThat(balance("CASH")).isEqualByComparingTo("80000");
        assertThat(jdbc.queryForObject(
                "select count(*) from finance_movements where source_type='PURCHASE_PAYMENT'",Long.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select count(*) from finance_movements where source_type='PAYMENT' and source_id=?",
                Long.class,payment.id())).isEqualTo(1);
        var after=cashFlow.summary(from,to);
        assertThat(after.purchasePayments().subtract(baseline.purchasePayments()))
                .isEqualByComparingTo("120000");
        assertThat(after.payments().subtract(baseline.payments()))
                .isEqualByComparingTo("42500");
        assertThat(after.totalOut().subtract(baseline.totalOut()))
                .isEqualByComparingTo("120000");
        assertThat(after.netCashFlow().subtract(baseline.netCashFlow()))
                .isEqualByComparingTo("-77500");
    }

    private CreatePurchasePaymentRequest supplierPayment(long amount) {
        return new CreatePurchasePaymentRequest(UUID.randomUUID(),
                PurchasePaymentKind.SUPPLIER,FinanceAccount.BANK,
                BigDecimal.valueOf(amount),null,null);
    }

    private CreatePurchaseReceiptRequest receipt(PurchaseResponse purchase,UUID warehouse,long quantity) {
        return new CreatePurchaseReceiptRequest(UUID.randomUUID(),warehouse,
                List.of(new ReceiptItemRequest(purchase.items().getFirst().id(),quantity)),null);
    }
    private BigDecimal balance(String account) {
        return jdbc.queryForObject("select balance from finance_accounts where code=?",BigDecimal.class,account);
    }
    private void clear() {
        jdbc.execute("""
                TRUNCATE TABLE
                    inventory_transfer_cost_origins, inventory_transfers,
                    inventory_cost_movements,inventory_cost_allocations,
                    inventory_cost_restorations,inventory_cost_write_offs,
                    inventory_cost_layers,exchange_settlements,exchanges,refunds,
                    return_items,returns,payments,deliveries,sale_items,sales,
                    defects,purchase_payments,purchase_receipt_items,
                    purchase_receipts,purchase_items,purchases,stock_movements,
                    inventory_balances
                RESTART IDENTITY
                """);
        jdbc.update("""
                delete from audit_entries
                where entity_type in ('PURCHASE','PURCHASE_RECEIPT','PAYMENT','SALE',
                                      'STOCK_MOVEMENT')
                """);
    }
}
