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
    @Autowired JdbcTemplate jdbc;

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
                .andExpect(jsonPath("$.netProfitAvailable").value(false))
                .andExpect(jsonPath("$.operatingProfit").doesNotExist())
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
            .andExpect(jsonPath("$.netProfitAvailable").value(false));
    }

}
