package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
 "CHAIRX_CATALOG_PASSWORD=integration-test-password",
 "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class FinanceProfitabilityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired kg.chairx.finance.application.FinanceProfitabilityService profitability;
    @Autowired JdbcTemplate jdbc;
    @Autowired kg.chairx.finance.web.FinancePurchaseObligationsController obligations;

    @Test void profitabilityIsProtectedAndDoesNotPretendUnreconciledPnlIsReady() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class))
                .isEqualTo("chairx_test");
        // Empty distant period allows deterministic verification independent
        // of seeded operational fixtures.
        String from="2024-01-01",to="2024-02-01";
        mvc.perform(get("/api/finance/profitability").param("from",from).param("to",to)
                .with(user("reader").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossRevenue").value(0))
                .andExpect(jsonPath("$.netProfitAvailable").value(true))
                .andExpect(jsonPath("$.operatingProfit").value(0))
                .andExpect(jsonPath("$.sales").isArray());
        mvc.perform(get("/api/finance/profitability").param("from",from).param("to",to)
                .with(user("warehouse").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"))))
                .andExpect(status().isForbidden());
    }
    @Test void fifoInventoryValuationRequiresFinancialAccess() throws Exception {
        mvc.perform(get("/api/finance/inventory-valuation")
                .with(user("finance").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").isNumber())
                .andExpect(jsonPath("$.models").isArray());
        mvc.perform(get("/api/finance/inventory-valuation")
                .with(user("stock").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test void purchaseLiabilitiesRequireFinancialAccess() throws Exception {
        mvc.perform(get("/api/finance/purchase-obligations")
                .with(user("finance").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").isNumber())
                .andExpect(jsonPath("$.purchases").isArray());
        mvc.perform(get("/api/finance/purchase-obligations")
                .with(user("stock").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test void fulfilledUnpaidSaleIsRecognizedButMissingFifoBlocksProfit() throws Exception {
        java.util.UUID product=java.util.UUID.randomUUID();
        java.util.UUID variant=java.util.UUID.randomUUID();
        java.util.UUID sale=java.util.UUID.randomUUID();
        java.util.UUID warehouse=jdbc.queryForObject(
                "SELECT id FROM warehouses WHERE code='HOME'",java.util.UUID.class);
        var day=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Bishkek"));
        jdbc.update("INSERT INTO products(id,name,active,created_at,updated_at) VALUES (?,'Profit fixture',TRUE,now(),now())",product);
        jdbc.update("INSERT INTO product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at) VALUES (?,?,'Default',1000,TRUE,now(),now())",variant,product);
        jdbc.update("""
             INSERT INTO sales(id,sale_number,idempotency_key,request_fingerprint,fulfillment_type,status,created_by,created_at,fulfilled_by,fulfilled_at)
             VALUES (?,?,?,?,'SELF_PICKUP','FULFILLED','report-test',now(),'report-test',now())
             """,sale,"REPORT-"+sale.toString().substring(0,13),java.util.UUID.randomUUID(),"report-test");
        jdbc.update("""
             INSERT INTO sale_items(id,sale_id,product_variant_id,warehouse_id,quantity,unit_sale_price)
             VALUES (?,?,?,?,1,1000)
             """,java.util.UUID.randomUUID(),sale,variant,warehouse);
        mvc.perform(get("/api/finance/profitability")
                .param("from",day.toString()).param("to",day.plusDays(1).toString())
                .with(user("accountant").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales[?(@.saleId == '"+sale+"')].recognizedRevenue").exists())
                .andExpect(jsonPath("$.completeCost").value(false))
                .andExpect(jsonPath("$.operatingProfit").doesNotExist());
        mvc.perform(get("/api/finance/model-margins")
                .param("from",day.toString()).param("to",day.plusDays(1).toString())
                .with(user("accountant").authorities(
                  new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.variantId == '"+variant+"')].revenue").exists());
        mvc.perform(get("/api/finance/model-margins")
                .param("from",day.toString()).param("to",day.plusDays(1).toString())
                .with(user("warehouse").authorities(
                  new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test void deliveredOrderRecognizesGoodsAndFeeSeparatelyWithoutCreatingCashPayments() throws Exception {
        var date=java.time.LocalDate.of(2024,7,11);
        var recognizedAt=java.time.OffsetDateTime.parse("2024-07-11T13:00:00+06:00");
        var warehouse=jdbc.queryForObject(
                "SELECT id FROM warehouses WHERE code='HOME'",java.util.UUID.class);
        var product=java.util.UUID.randomUUID();
        var variant=java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO products(id,name,active,created_at,updated_at) VALUES (?,'Pnl delivery fixture',TRUE,now(),now())",product);
        jdbc.update("INSERT INTO product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at) VALUES (?,?,'Default',7000,TRUE,now(),now())",variant,product);
        var deliveredSale=java.util.UUID.randomUUID();
        var pickupSale=java.util.UUID.randomUUID();
        for (var fixture:java.util.List.of(
                new Object[]{deliveredSale,"CITY_DELIVERY",7000},
                new Object[]{pickupSale,"SELF_PICKUP",2000})) {
            var id=(java.util.UUID)fixture[0];
            jdbc.update("""
                INSERT INTO sales(id,sale_number,idempotency_key,request_fingerprint,
                    fulfillment_type,status,created_by,created_at,fulfilled_by,fulfilled_at)
                VALUES (?,?,?,? ,?,'FULFILLED','pnl-delivery-test',?,'pnl-delivery-test',?)
                """,id,"P&L-"+id.toString().substring(0,13),
                java.util.UUID.randomUUID(),"pnl-delivery-fixture",fixture[1],recognizedAt,recognizedAt);
            jdbc.update("""
                INSERT INTO sale_items(id,sale_id,product_variant_id,warehouse_id,quantity,unit_sale_price)
                VALUES (?,?,?,?,1,?)
                """,java.util.UUID.randomUUID(),id,variant,warehouse,fixture[2]);
        }
        jdbc.update("""
            INSERT INTO deliveries(id,sale_id,status,recipient_name,recipient_phone,address,
                delivery_cost,created_by,created_at,dispatched_by,dispatched_at,delivered_by,delivered_at)
            VALUES (?,?,'DELIVERED','Customer','+996555000000','Bishkek',
                1000,'pnl-delivery-test',?,'pnl-delivery-test',?,'pnl-delivery-test',?)
            """,java.util.UUID.randomUUID(),deliveredSale,recognizedAt,recognizedAt,recognizedAt);

        var report=profitability.calculate(date,date.plusDays(1));
        var delivery=report.sales().stream().filter(x->x.saleId().equals(deliveredSale)).findFirst().orElseThrow();
        var pickup=report.sales().stream().filter(x->x.saleId().equals(pickupSale)).findFirst().orElseThrow();
        assertThat(delivery.goodsRevenue()).isEqualByComparingTo("7000");
        assertThat(delivery.deliveryRevenue()).isEqualByComparingTo("1000");
        assertThat(delivery.recognizedRevenue()).isEqualByComparingTo("8000");
        assertThat(pickup.goodsRevenue()).isEqualByComparingTo("2000");
        assertThat(pickup.deliveryRevenue()).isEqualByComparingTo("0");
        assertThat(pickup.recognizedRevenue()).isEqualByComparingTo("2000");
        assertThat(report.goodsRevenue().add(report.deliveryRevenue()))
                .isEqualByComparingTo(report.grossRevenue());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE sale_id IN (?,?)",
                Long.class,deliveredSale,pickupSale)).isZero();
        mvc.perform(get("/api/finance/profitability")
                .param("from",date.toString()).param("to",date.plusDays(1).toString())
                .with(user("accountant").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goodsRevenue").isNumber())
                .andExpect(jsonPath("$.deliveryRevenue").isNumber())
                .andExpect(jsonPath("$.sales[?(@.saleId == '"+deliveredSale+"')].deliveryRevenue").exists());
    }

    @Test void linkedExpenseIsNotChargedTwiceWhenAccrualIsRecognized() throws Exception {
        var accrual=java.util.UUID.randomUUID();
        var linked=java.util.UUID.randomUUID();
        var other=java.util.UUID.randomUUID();
        jdbc.update("""
            INSERT INTO expense_accruals(id,idempotency_key,category,description,amount,
                period_start,period_end,created_by)
            VALUES (?,?,'RENT','Office rent',900,'2024-05-01','2024-05-04','finance-test')
            """,accrual,java.util.UUID.randomUUID());
        jdbc.update("""
            INSERT INTO expenses(id,category,amount,payment_method,expense_date,comment,created_by,created_at)
            VALUES (?,'RENT',900,'CASH','2024-05-02','Rent payment','finance-test',now())
            """,linked);
        jdbc.update("""
            INSERT INTO expenses(id,category,amount,payment_method,expense_date,comment,created_by,created_at)
            VALUES (?,'OTHER',100,'BANK','2024-05-02','Separate expense','finance-test',now())
            """,other);
        jdbc.update("""
            INSERT INTO expense_accrual_payments(accrual_id,expense_id,linked_by)
            VALUES (?,?,'finance-test')
            """,accrual,linked);
        mvc.perform(get("/api/finance/profitability")
            .param("from","2024-05-01").param("to","2024-05-04")
            .with(user("accountant").authorities(
              new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recognizedAccruals").value(900))
            .andExpect(jsonPath("$.otherOperatingExpenses").value(100))
            .andExpect(jsonPath("$.netProfitAvailable").value(true))
            .andExpect(jsonPath("$.operatingProfit").value(-1000));
    }

    @Test void purchaseSupplierDebtAndCargoAdvanceAreKeptSeparate() throws Exception {
        var supplier=java.util.UUID.randomUUID();
        var model=java.util.UUID.randomUUID();
        var variant=java.util.UUID.randomUUID();
        var purchase=java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO suppliers(id,name,active,created_at,updated_at) VALUES (?,'Report supplier',TRUE,now(),now())",supplier);
        jdbc.update("INSERT INTO products(id,name,active,created_at,updated_at) VALUES (?,'Report product',TRUE,now(),now())",model);
        jdbc.update("INSERT INTO product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at) VALUES (?,?,'Default',700,TRUE,now(),now())",variant,model);
        jdbc.update("""
            INSERT INTO purchases(id,supplier_id,status,cargo_cost,cargo_allocation_method,comment,created_by,created_at,updated_at)
            VALUES (?,?,'CONFIRMED',100,'BY_QUANTITY','Report order','report-test',now(),now())
            """,purchase,supplier);
        jdbc.update("""
            INSERT INTO purchase_items(id,purchase_id,product_variant_id,line_number,ordered_quantity,purchase_unit_cost)
            VALUES (?,?,?,1,2,300)
            """,java.util.UUID.randomUUID(),purchase,variant);
        for(var item:java.util.List.of(new Object[]{"SUPPLIER",400},new Object[]{"CARGO",130})) {
            jdbc.update("""
                INSERT INTO purchase_payments(id,purchase_id,payment_kind,account_code,amount,
                    idempotency_key,request_fingerprint,created_by)
                VALUES (?, ?, ?, 'CASH', ?, ?, ?, 'report-test')
                """,java.util.UUID.randomUUID(),purchase,item[0],item[1],
                java.util.UUID.randomUUID(),"a".repeat(64));
        }
        mvc.perform(get("/api/finance/purchase-obligations")
          .with(user("accountant").authorities(
             new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.purchases[?(@.purchaseId == '"+purchase+"')].outstanding").exists())
          .andExpect(jsonPath("$.purchases[?(@.purchaseId == '"+purchase+"')].prepaid").exists());
        var line=obligations.list().purchases().stream().filter(p->p.purchaseId().equals(purchase))
                .findFirst().orElseThrow();
        assertThat(line.outstanding()).isEqualByComparingTo("200");
        assertThat(line.prepaid()).isEqualByComparingTo("30");
    }

    @Test void monthlyAnalyticsSeparatesCashFromProfitAndGuardsPermissions() throws Exception {
        mvc.perform(get("/api/finance/analytics")
                .param("from","2024-01-01").param("to","2024-03-01")
                .with(user("finance").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months.length()").value(2))
                .andExpect(jsonPath("$.months[0].cashIn").isNumber())
                .andExpect(jsonPath("$.months[0].recognizedRevenue").isNumber())
                .andExpect(jsonPath("$.months[0].profitVerified").isBoolean());
        mvc.perform(get("/api/finance/analytics")
                .param("from","2024-01-01").param("to","2024-03-01")
                .with(user("stock").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/finance/analytics")
                .param("from","2024-01-02").param("to","2024-03-01")
                .with(user("finance").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
                .andExpect(status().isBadRequest());
    }

}
