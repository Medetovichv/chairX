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
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "CHAIRX_CATALOG_PASSWORD=integration-test-password",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class InventoryUnifiedSearchApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void findByModelVariantAndSkuBeforePaginationAndNeverReturnCost() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("chairx_test");
        String suffix=UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        UUID product=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO products(id,name,active,created_at,updated_at)
            VALUES (?, ?, true, now(), now())
            """, product, "SEARCH Chair " + suffix);
        UUID black=UUID.randomUUID(), beige=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO product_variants(
                id,product_id,name,sku,recommended_sale_price,active,created_at,updated_at)
            VALUES (?, ?, 'Чёрный', ?, 8500, true, now(), now()),
                   (?, ?, 'Бежевый', ?, 9900, true, now(), now())
            """,black,product,"SKU_BLK_"+suffix,beige,product,"SKU_BEG_"+suffix);
        var read=user("stock-reader").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"));
        String root="/api/inventory/overview";
        mvc.perform(get(root).param("search","SKU_BLK_"+suffix)
              .param("includeZero","true").param("size","1").with(read))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].model").value("SEARCH Chair "+suffix))
            .andExpect(jsonPath("$.items[0].variation").value("Чёрный"))
            .andExpect(jsonPath("$.items[0].productVariantId").value(black.toString()))
            .andExpect(jsonPath("$.items[0].totalCost").doesNotExist());
        mvc.perform(get(root).param("search","Бежев").param("includeZero","true").with(read))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].variation").value("Бежевый"));
        mvc.perform(get(root).param("search","SEARCH Chair "+suffix)
              .param("includeZero","true").with(read))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2));
        // A percent sign is a literal search character, never a SQL wildcard.
        mvc.perform(get(root).param("search","%").param("includeZero","true").with(read))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get(root).param("search","SKU_BLK_"+suffix)
              .param("includeZero","true").with(user("unrelated").authorities(
                  new org.springframework.security.core.authority.SimpleGrantedAuthority("CUSTOMERS_READ"))))
            .andExpect(status().isForbidden());
    }
}
