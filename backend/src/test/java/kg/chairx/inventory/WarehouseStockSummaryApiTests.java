package kg.chairx.inventory;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
class WarehouseStockSummaryApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static final String URL = "/api/inventory/warehouse-summary";

    @Test
    void summaryIsRestrictedAndCostFree() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("chairx_test");
        mvc.perform(get(URL).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).with(user("cashier").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
                .andExpect(status().isForbidden());
        mvc.perform(post(URL).with(user("warehouse").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ")))
                .with(csrf())).andExpect(status().isForbidden());

        var result = mvc.perform(get(URL).with(user("warehouse").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("totalCost", "unitCost", "purchasePrice",
                "costOrigin", "margin", "passwordHash", "accountBalance");
        long expected = jdbc.queryForObject("SELECT count(*) FROM warehouses", Long.class);
        // Every warehouse appears, including inactive and empty locations.
        long actual = new tools.jackson.databind.json.JsonMapper().readTree(result).size();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void summaryCountsAllBalancesBeyondFirstPageAndRespectsReservedBlocked() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("chairx_test");
        UUID warehouseId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO warehouses(id,name,code,active,created_at,updated_at)
                VALUES (?, 'F13 stock summary', ?, TRUE, now(), now())
                """, warehouseId,"UI4-"+warehouseId.toString().substring(0,8));
        // We cannot rely on catalog fixtures: create our own real product/variants
        // through insertable schema and verify the FULL aggregate, not page 1.
        UUID product=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products(id,name,active,created_at,updated_at)
                VALUES (?, ?, TRUE, now(), now())
                """,product,"UI4 Product "+product.toString().substring(0,8));
        for(int i=0;i<25;i++) {
            UUID variant=UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO product_variants(id,product_id,name,active,created_at,updated_at)
                    VALUES (?,?,?,TRUE,now(),now())
                    """, variant,product,"UI4 variant "+i);
            jdbc.update("""
                    INSERT INTO inventory_balances(warehouse_id,product_variant_id,on_hand,reserved,blocked)
                    VALUES (?,?,?,?,?)
                    """,warehouseId,variant,10,2,1);
        }
        var body=mvc.perform(get(URL).with(user("warehouse").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var data=new tools.jackson.databind.json.JsonMapper().readTree(body);
        var selected=java.util.stream.StreamSupport.stream(data.spliterator(),false)
                .filter(x->warehouseId.toString().equals(x.get("warehouseId").asText()))
                .findFirst().orElseThrow();
        assertThat(selected.get("onHand").asLong()).isEqualTo(250);
        assertThat(selected.get("reserved").asLong()).isEqualTo(50);
        assertThat(selected.get("blocked").asLong()).isEqualTo(25);
        assertThat(selected.get("available").asLong()).isEqualTo(175);
        assertThat(selected.get("stockedVariants").asLong()).isEqualTo(25);
        assertThat(selected.get("lowStockVariants").asLong()).isZero();
    }
}
