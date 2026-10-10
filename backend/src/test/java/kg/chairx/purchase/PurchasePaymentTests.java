package kg.chairx.purchase;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.purchase.api.*;
import kg.chairx.purchase.application.*;
import kg.chairx.purchase.domain.*;
import kg.chairx.finance.domain.FinanceAccount;
import kg.chairx.inventory.application.InventoryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.junit.jupiter.api.extension.ExtendWith(kg.chairx.FundedFinanceExtension.class)
@SpringBootTest(properties={
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class PurchasePaymentTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PurchaseService purchases;
    @Autowired PurchasePaymentService payments;
    @Autowired InventoryService inventory;
    @Autowired MockMvc mvc;

    UUID supplier,product,variant,home;

    @BeforeEach void fixture() {
        assertThat(jdbc.queryForObject("select current_database()",String.class))
                .isEqualTo("chairx_test");
        authenticate();
        supplier=UUID.randomUUID();
        product=UUID.randomUUID();
        variant=UUID.randomUUID();
        home=jdbc.queryForObject("select id from warehouses where code='HOME'",UUID.class);
        jdbc.update("insert into suppliers(id,name,active,created_at,updated_at) values(?,'P20 Supplier',true,now(),now())",supplier);
        jdbc.update("insert into products(id,name,active,created_at,updated_at) values(?,'P20 Product',true,now(),now())",product);
        jdbc.update("""
                insert into product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at)
                values(?,?, 'P20 Chair',8500,true,now(),now())
                """,variant,product);
    }
    @AfterEach void cleanup() {
        jdbc.update("delete from purchase_payments where purchase_id in (select id from purchases where supplier_id=?)",supplier);
        jdbc.update("delete from purchase_receipt_items where purchase_id in (select id from purchases where supplier_id=?)",supplier);
        jdbc.update("delete from purchase_receipts where purchase_id in (select id from purchases where supplier_id=?)",supplier);
        jdbc.update("delete from purchase_items where purchase_id in (select id from purchases where supplier_id=?)",supplier);
        jdbc.update("delete from purchases where supplier_id=?",supplier);
        jdbc.update("delete from product_variants where id=?",variant);
        jdbc.update("delete from products where id=?",product);
        jdbc.update("delete from suppliers where id=?",supplier);
        SecurityContextHolder.clearContext();
    }

    @Test void partialPaymentsDecrementBankAndCashButNeverInventory() {
        var p=confirmed("10000");
        long stockBefore=inventory.getBalance(home,variant).onHand();
        var first=payments.create(p.id(),request("SUPPLIER","BANK",30000,UUID.randomUUID()));
        var second=payments.create(p.id(),request("SUPPLIER","BANK",20000,UUID.randomUUID()));
        var cargo=payments.create(p.id(),request("CARGO","CASH",10000,UUID.randomUUID()));
        var report=payments.list(p.id());

        assertThat(report.supplierTotal()).isEqualByComparingTo("50000");
        assertThat(report.cargoTotal()).isEqualByComparingTo("10000");
        assertThat(report.paidSupplier()).isEqualByComparingTo("50000");
        assertThat(report.paidCargo()).isEqualByComparingTo("10000");
        assertThat(report.remainingSupplier()).isEqualByComparingTo("0");
        assertThat(report.remainingCargo()).isEqualByComparingTo("0");
        assertThat(report.payments()).hasSize(3);
        assertThat(balance("CASH")).isEqualByComparingTo("990000");
        assertThat(balance("BANK")).isEqualByComparingTo("950000");
        assertThat(inventory.getBalance(home,variant).onHand()).isEqualTo(stockBefore);
        for(UUID id:List.of(first.id(),second.id(),cargo.id()))
            assertThat(jdbc.queryForObject("""
                    select count(*) from finance_movements
                    where source_type='PURCHASE_PAYMENT' and source_id=?
                    """,Long.class,id)).isEqualTo(1);
    }

    @Test void retryAndChangedPayloadDoNotDoubleDebit() {
        var p=confirmed("10000");
        UUID key=UUID.randomUUID();
        var req=request("SUPPLIER","BANK",15000,key);
        var first=payments.create(p.id(),req);
        var again=payments.create(p.id(),req);
        assertThat(again.id()).isEqualTo(first.id());
        assertThatThrownBy(()->payments.create(p.id(),request("SUPPLIER","BANK",16000,key)))
                .isInstanceOfSatisfying(PurchaseRuleViolationException.class,
                        e->assertThat(e.getCode()).isEqualTo("PURCHASE_PAYMENT_IDEMPOTENCY_CONFLICT"));
        assertThat(balance("BANK")).isEqualByComparingTo("985000");
        assertThat(jdbc.queryForObject(
                "select count(*) from finance_movements where source_id=?",Long.class,first.id()))
                .isEqualTo(1);
    }

    @Test void overpaymentCancellationAndCargoReductionAreRejected() {
        var p=confirmed("10000");
        payments.create(p.id(),request("SUPPLIER","BANK",40000,UUID.randomUUID()));
        assertThatThrownBy(()->payments.create(p.id(),request("SUPPLIER","BANK",11000,UUID.randomUUID())))
                .isInstanceOf(PurchaseRuleViolationException.class);
        assertThatThrownBy(()->purchases.cancel(p.id()))
                .isInstanceOfSatisfying(PurchaseRuleViolationException.class,
                        e->assertThat(e.getCode()).isEqualTo("PAID_PURCHASE_CANNOT_CANCEL"));
        payments.create(p.id(),request("CARGO","CASH",8000,UUID.randomUUID()));
        assertThatThrownBy(()->purchases.setCargo(p.id(),new SetCargoCostRequest(BigDecimal.valueOf(7000))))
                .isInstanceOfSatisfying(PurchaseRuleViolationException.class,
                        e->assertThat(e.getCode()).isEqualTo("PURCHASE_CARGO_BELOW_PAID"));
        assertThat(payments.list(p.id()).payments()).hasSize(2);
    }

    @Test void postingFailureRollsBackPaymentDocument() {
        var p=confirmed("10000");
        jdbc.update("update finance_accounts set balance=1 where code='BANK'");
        UUID key=UUID.randomUUID();
        assertThatThrownBy(()->payments.create(p.id(),request("SUPPLIER","BANK",5000,key)))
                .isInstanceOf(kg.chairx.finance.domain.FinanceAccountOperationException.class);
        assertThat(jdbc.queryForObject(
                "select count(*) from purchase_payments where idempotency_key=?",Long.class,key))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from finance_movements where source_type='PURCHASE_PAYMENT'",Long.class))
                .isZero();
        assertThat(balance("BANK")).isEqualByComparingTo("1");
    }

    @Test void parallelRequestsWithSameKeyCreateOnePayment() throws Exception {
        var p=confirmed("10000");
        UUID key=UUID.randomUUID();
        var req=request("SUPPLIER","BANK",4000,key);
        var ready=new CountDownLatch(2);
        var start=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        Callable<UUID> action=()->{
            authenticate();
            try {
                ready.countDown();
                if(!start.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                return payments.create(p.id(),req).id();
            } finally { SecurityContextHolder.clearContext(); }
        };
        try {
            var a=executor.submit(action);
            var b=executor.submit(action);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
        } finally {start.countDown();executor.shutdownNow();}
        assertThat(payments.list(p.id()).payments()).hasSize(1);
        assertThat(balance("BANK")).isEqualByComparingTo("996000");
    }

    @Test void purchasePaymentsAreAvailableOverHttp() throws Exception {
        var p=confirmed("10000");
        String payload="""
                {"idempotencyKey":"%s","paymentKind":"SUPPLIER","account":"BANK",
                 "amount":3000,"reference":"MBANK-123","comment":"Advance"}
                """.formatted(UUID.randomUUID());
        mvc.perform(post("/api/purchases/"+p.id()+"/payments")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/purchases/"+p.id()+"/payments")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("accountant"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentKind").value("SUPPLIER"));
        mvc.perform(get("/api/purchases/"+p.id()+"/payments")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("accountant")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSupplier").value(47000));
    }

    private PurchaseResponse confirmed(String cargo) {
        var p=purchases.create(new CreatePurchaseRequest(supplier,
                List.of(new PurchaseItemRequest(variant,10,new BigDecimal("5000"))),
                new BigDecimal(cargo),null));
        return purchases.confirm(p.id());
    }
    private CreatePurchasePaymentRequest request(String kind,String account,long amount,UUID key) {
        return new CreatePurchasePaymentRequest(key,PurchasePaymentKind.valueOf(kind),
                FinanceAccount.valueOf(account),BigDecimal.valueOf(amount),"MBANK-123",null);
    }
    private BigDecimal balance(String code) {
        return jdbc.queryForObject("select balance from finance_accounts where code=?",BigDecimal.class,code);
    }
    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("purchase-payment-tester",null,List.of()));
    }
}
