package kg.chairx.dashboard;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class DashboardOverviewIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static final String PATH = "/api/dashboard/overview";

    @Test
    void permissionGatesPreventCrossModuleDataDisclosure() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("chairx_test");

        mvc.perform(get(PATH).with(anonymous()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(user("no-dashboard-rights").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("CATALOG_READ"))))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(user("sales-operator").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("SALES_READ")))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        var inventoryUser = user("warehouse").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"));
        var warehouse = mvc.perform(get(PATH).with(inventoryUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableUnits").isNumber())
                .andExpect(jsonPath("$.lowStockVariants").isNumber())
                .andExpect(jsonPath("$.ordersByDay").isEmpty())
                .andExpect(jsonPath("$.recentSales").isEmpty())
                .andExpect(jsonPath("$.orders30Days").doesNotExist())
                .andExpect(jsonPath("$.pendingDeliveries").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(warehouse).doesNotContain("customerName", "saleNumber", "totalCost",
                "purchaseUnitCost", "profitMargin", "passwordHash");

        mvc.perform(get(PATH).with(user("courier").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("DELIVERIES_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingDeliveries").isNumber())
                .andExpect(jsonPath("$.delayedDeliveries").isNumber())
                .andExpect(jsonPath("$.recentSales").isEmpty())
                .andExpect(jsonPath("$.orders30Days").doesNotExist())
                .andExpect(jsonPath("$.availableUnits").doesNotExist());
    }

    @Test
    void realSaleIsReflectedInBusinessDaySeriesAndLatestOrdersWithoutMutation() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("chairx_test");
        UUID saleId = UUID.randomUUID();
        String number = "DASH-" + saleId.toString().substring(0, 8);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        long salesBefore = jdbc.queryForObject("SELECT count(*) FROM sales", Long.class);
        jdbc.update("""
                INSERT INTO sales(id, sale_number, status, idempotency_key,
                                  request_fingerprint, created_by, created_at)
                VALUES (?, ?, 'DRAFT', ?, ?, 'dashboard-test',
                        clock_timestamp())
                """, saleId, number, UUID.randomUUID(), "f13-ui-v2-test");

        var content = mvc.perform(get(PATH).with(user("manager").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("SALES_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value(today.toString()))
                .andExpect(jsonPath("$.orders30Days").isNumber())
                .andExpect(jsonPath("$.activeOrders").isNumber())
                .andExpect(jsonPath("$.ordersByDay.length()").value(7))
                .andExpect(jsonPath("$.recentSales").isArray())
                .andExpect(jsonPath("$.availableUnits").doesNotExist())
                .andExpect(jsonPath("$.pendingDeliveries").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(content).contains(number);
        assertThat(content).doesNotContain("totalCost", "profitMargin", "purchaseUnitCost");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales", Long.class))
                .isEqualTo(salesBefore + 1);
    }
}
