package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.finance.application.EmployeeDailyClosingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * #107: old employee closing snapshots must not reconstruct their historic
 * delivery split using the mutable delivery record. Disposable PG only.
 */
@SpringBootTest(properties={
    "CHAIRX_CATALOG_PASSWORD=integration-test-password",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class EmployeeClosingImmutableComponentsIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EmployeeDailyClosingService service;

    private final UUID userId=UUID.randomUUID();
    private final UUID closeId=UUID.randomUUID();
    private final UUID saleWithFrozenValues=UUID.randomUUID();
    private final UUID legacySale=UUID.randomUUID();
    private final String username="historical_components_"+userId.toString().replace("-","").substring(0,14);
    private final LocalDate day=LocalDate.now(ZoneId.of("Asia/Bishkek"));

    private void assertTestDb() {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class))
            .isEqualTo("chairx_test");
    }

    @Test void recordsKnownComponentsAndKeepsUnknownLegacySplitUnknown() {
        assertTestDb();
        jdbc.update("""
            INSERT INTO app_users(id,username,password_hash,display_name,active)
            VALUES (?,?,'{noop}integration-test','Finance snapshot fixture',TRUE)
            """,userId,username);
        jdbc.update("""
            INSERT INTO employee_daily_closings(
                id,employee_id,business_date,expected_cash,expected_bank,
                actual_cash,actual_bank,company_wide)
            VALUES (?,?,?,0,0,0,0,TRUE)
            """,closeId,userId,day);
        Instant completed=Instant.parse("2020-10-10T08:00:00Z");
        jdbc.update("""
            INSERT INTO employee_daily_closing_sales(
                closing_id,sale_id,sale_number,quantity,total,completed_at,
                goods_total,delivery_total,fulfillment,delivery_status,payment_status)
            VALUES (?,?,?,1,8000,?,7000,1000,'REGION_DELIVERY','READY','PAID')
            """,closeId,saleWithFrozenValues,"HIST-1",completed.atOffset(java.time.ZoneOffset.UTC));
        jdbc.update("""
            INSERT INTO employee_daily_closing_sales(
                closing_id,sale_id,sale_number,quantity,total,completed_at,
                fulfillment,delivery_status,payment_status)
            VALUES (?,?,?,1,8000,?,'REGION_DELIVERY','READY','PAID')
            """,closeId,legacySale,"HIST-2",completed.atOffset(java.time.ZoneOffset.UTC));

        var saved=service.companyReport(username,day);
        assertThat(saved.closed()).isTrue();
        assertThat(saved.sales()).hasSize(2);
        var known=saved.sales().stream().filter(row->row.saleId().equals(saleWithFrozenValues))
            .findFirst().orElseThrow();
        assertThat(known.total()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(known.goodsTotal()).isEqualByComparingTo(new BigDecimal("7000"));
        assertThat(known.deliveryTotal()).isEqualByComparingTo(new BigDecimal("1000"));
        var legacy=saved.sales().stream().filter(row->row.saleId().equals(legacySale))
            .findFirst().orElseThrow();
        assertThat(legacy.total()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(legacy.goodsTotal()).isNull();
        assertThat(legacy.deliveryTotal()).isNull();
    }

    @AfterEach void cleanup() {
        assertTestDb();
        jdbc.update("DELETE FROM employee_daily_closing_sales WHERE closing_id=?",closeId);
        jdbc.update("DELETE FROM employee_daily_closings WHERE id=?",closeId);
        jdbc.update("DELETE FROM app_users WHERE id=?",userId);
    }
}
