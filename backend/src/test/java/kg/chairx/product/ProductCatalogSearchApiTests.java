package kg.chairx.product;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class ProductCatalogSearchApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void searchesAllPagesAndFiltersCategoryAndStatusWithoutExposingStockCost() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("chairx_test");
        String token=UUID.randomUUID().toString().substring(0,8);
        for (int index=0;index<25;index++) {
            String name=index==24 ? "UI6 Unique "+token : "UI6 Model "+index+" "+token;
            jdbc.update("""
                INSERT INTO products(id,name,category,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, now(), now())
                """,UUID.randomUUID(),name,index==24?"Премиум":"Офисные",index!=24);
        }
        var authorized=user("catalog-reader").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("CATALOG_READ"));
        var response=mvc.perform(get("/api/products")
                .param("search","Unique "+token)
                .param("size","1").param("active","false").with(authorized))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].name").value("UI6 Unique "+token))
                .andExpect(jsonPath("$.items[0].category").value("Премиум"))
                .andExpect(jsonPath("$.items[0].active").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("totalCost","purchaseUnitCost","profitMargin","passwordHash");
        mvc.perform(get("/api/products").param("category","пРеМиУм")
                .param("search",token).with(authorized))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/products").param("search","Unique "+token)
                .param("active","true").with(authorized))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/products").param("search",token)
                .param("category","Премиум").with(user("warehouse").authorities(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void percentAndUnderscoreAreLiteralSearchCharacters() throws Exception {
        String token=UUID.randomUUID().toString().substring(0,8);
        jdbc.update("""
            INSERT INTO products(id,name,category,active,created_at,updated_at)
            VALUES (?,?,NULL,true,now(),now()),(?,?,NULL,true,now(),now())
            """,UUID.randomUUID(),"UI6%"+token,UUID.randomUUID(),"UI6a"+token);
        var auth=user("catalog-reader").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("CATALOG_READ"));
        mvc.perform(get("/api/products").param("search","UI6%"+token).with(auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].name").value("UI6%"+token));
    }
}
