package kg.chairx.sale;

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
class SaleStatisticsIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void statisticsReadRequiresSalesPermissionAndDoesNotLeakPrivateCost() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        String url="/api/sales/statistics";
        mvc.perform(get(url).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(url).with(user("warehouse").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"))))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).with(user("manager").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SALES_READ")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        var result=mvc.perform(get(url).with(user("manager").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SALES_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders30Days").isNumber())
                .andExpect(jsonPath("$.averageCheck30Days").isNumber())
                .andExpect(jsonPath("$.fulfilled30Days").isNumber())
                .andExpect(jsonPath("$.inWork30Days").isNumber())
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("totalCost","purchaseUnitCost","margin","customerName",
                "warehouseId", "passwordHash", "profit");
    }

    @Test
    void serverCountsRealSalesAndAverageOnlyForConfirmedAndFulfilled() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        String url="/api/sales/statistics";
        var asSales=user("manager").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SALES_READ"));
        var mapper=new tools.jackson.databind.json.JsonMapper();
        var before=mapper.readTree(mvc.perform(get(url).with(asSales))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long orderBefore=before.get("orders30Days").asLong();
        long workBefore=before.get("inWork30Days").asLong();

        UUID draft=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sales(id,sale_number,fulfillment_type,status,idempotency_key,
                                  request_fingerprint,created_by,created_at)
                VALUES (? ,?, 'SELF_PICKUP','DRAFT',?,?, 'statistics-test',clock_timestamp())
                """,draft,"UI3-"+draft.toString().substring(0,8),UUID.randomUUID(),"f13-stats-draft");
        UUID confirmed=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sales(id,sale_number,fulfillment_type,status,idempotency_key,
                                  request_fingerprint,created_by,created_at)
                VALUES (? ,?, 'SELF_PICKUP','CONFIRMED',?,?, 'statistics-test',clock_timestamp())
                """,confirmed,"UI3-"+confirmed.toString().substring(0,8),UUID.randomUUID(),"f13-stats-confirmed");

        var after=mapper.readTree(mvc.perform(get(url).with(asSales))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(after.get("orders30Days").asLong()).isEqualTo(orderBefore+2);
        assertThat(after.get("inWork30Days").asLong()).isEqualTo(workBefore+2);
        assertThat(after.get("averageCheck30Days").decimalValue()).isGreaterThanOrEqualTo(java.math.BigDecimal.ZERO);
        // No request changed sales or created financial/stock movements.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales WHERE id IN (?,?)",
                Long.class,draft,confirmed)).isEqualTo(2);
    }
}
