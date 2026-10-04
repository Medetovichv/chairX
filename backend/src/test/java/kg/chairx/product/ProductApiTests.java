package kg.chairx.product;

import java.util.UUID;
import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@WithMockUser(username = "catalog-tester")
class ProductApiTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearTestDatabase() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("chairx_test");
        jdbc.update("delete from audit_entries");
        jdbc.update("delete from product_variants");
        jdbc.update("delete from products");
    }

    @Test
    void productLifecyclePreservesIdentityAndAudit() throws Exception {
        String id = createProduct("Ergo X5");
        var initial = mvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String createdAt = mapper.readTree(initial).get("createdAt").asText();
        mvc.perform(put("/api/products/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" Ergo X5 Pro ","description":"Описание","category":"Офисные"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Ergo X5 Pro"))
                .andExpect(jsonPath("$.description").value("Описание"))
                .andExpect(jsonPath("$.category").value("Офисные"))
                .andExpect(jsonPath("$.createdAt").value(createdAt));
        mvc.perform(post("/api/products/" + id + "/deactivate").with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/api/products/" + id + "/deactivate").with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/products/" + id + "/activate").with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        assertThat(jdbc.queryForObject("select count(*) from products", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_entries", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select actor from audit_entries where action = 'CREATED'", String.class))
                .isEqualTo("catalog-tester");
        assertThat(jdbc.queryForObject("select before_state->>'name' from audit_entries where action = 'UPDATED'", String.class))
                .isEqualTo("Ergo X5");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void blankProductNameIsRejected(String name) throws Exception {
        mvc.perform(post("/api/products").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fields.name[0]").value("Укажите название"));
        assertThat(jdbc.queryForObject("select count(*) from products", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_entries", Integer.class)).isZero();
    }

    @Test
    void listsProductsWithBoundedStablePagination() throws Exception {
        createProduct("X5");
        createProduct("X7");
        mvc.perform(get("/api/products?page=0&size=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/products?page=2&size=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=hello", "size=2147483648"})
    void rejectsInvalidPagination(String query) throws Exception {
        mvc.perform(get("/api/products?" + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void variantLifecycleAndPriceHistory() throws Exception {
        String product = createProduct("X5");
        String id = createVariant(product, "Black", "9500.50");
        mvc.perform(get("/api/product-variants/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(product))
                .andExpect(jsonPath("$.recommendedSalePrice").value(9500.50))
                .andExpect(jsonPath("$.product").doesNotExist());
        mvc.perform(put("/api/product-variants/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Black updated","sku":" SKU-1 ","color":"Чёрный","recommendedSalePrice":9800.25}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.recommendedSalePrice").value(9800.25));
        mvc.perform(post("/api/product-variants/" + id + "/deactivate").with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/api/product-variants/" + id + "/activate").with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        assertThat(jdbc.queryForObject("""
                select before_state->>'recommendedSalePrice' from audit_entries
                where entity_id = ? and action = 'UPDATED'
                """, String.class, UUID.fromString(id))).isEqualTo("9500.50");
    }

    @Test
    void variantsAreScopedToProductAndDuplicateSkuIsAllowed() throws Exception {
        String first = createProduct("X5");
        String second = createProduct("X7");
        String variant = createVariant(first, "Black", "0");
        createVariant(second, "White", "10000");
        createVariant(first, "Grey", "9000");
        mvc.perform(get("/api/products/" + first + "/variants"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].productId", everyItem(is(first))))
                .andExpect(jsonPath("$.items[*].id", hasItem(variant)));
        mvc.perform(get("/api/products/" + first + "/variants?size=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void productDeactivationDoesNotRewriteVariantFlags() throws Exception {
        String product = createProduct("X5");
        String variant = createVariant(product, "Black", "100");
        mvc.perform(post("/api/products/" + product + "/deactivate").with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/product-variants/" + variant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void missingParentCannotCreateVariant() throws Exception {
        mvc.perform(post("/api/products/" + UUID.randomUUID() + "/variants").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Black\",\"recommendedSalePrice\":100}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Товар не найден"));
        assertThat(jdbc.queryForObject("select count(*) from product_variants", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_entries", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.001", "100000000000000000", "null"})
    void rejectsInvalidPrices(String price) throws Exception {
        String product = createProduct("X5");
        mvc.perform(post("/api/products/" + product + "/variants").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Black\",\"recommendedSalePrice\":" + price + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fields.recommendedSalePrice").isArray());
    }

    @Test
    void blankVariantNameAndInvalidUpdateLeaveDataUnchanged() throws Exception {
        String product = createProduct("X5");
        mvc.perform(post("/api/products/" + product + "/variants").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"recommendedSalePrice\":10}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fields.name").isArray());
        String variant = createVariant(product, "Black", "100");
        mvc.perform(put("/api/product-variants/" + variant).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Black\",\"recommendedSalePrice\":-10}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/product-variants/" + variant)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedSalePrice").value(100));
    }

    @Test
    void missingResourcesAndMalformedRequestsUseErrorContract() throws Exception {
        mvc.perform(get("/api/products/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mvc.perform(get("/api/product-variants/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_VARIANT_NOT_FOUND"));
        mvc.perform(get("/api/products/" + UUID.randomUUID() + "/variants"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mvc.perform(get("/api/products/not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/products").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/products").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X5\",\"active\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/not-found")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void deleteIsNotExposed() throws Exception {
        String id = createProduct("X5");
        mvc.perform(delete("/api/products/" + id).with(csrf())).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(get("/api/products/" + id)).andExpect(status().isOk());
    }

    @Test
    void securityFiltersUseJsonAndKeepCsrfProtection() throws Exception {
        mvc.perform(get("/api/products").with(anonymous())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X5\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void configuredBasicCredentialsAuthenticate() throws Exception {
        mvc.perform(get("/api/products").with(anonymous()).with(httpBasic("catalog", "integration-test-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products").with(anonymous()).with(httpBasic("catalog", "incorrect")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void realCsrfTokenWithItsSessionAllowsCreation() throws Exception {
        var tokenResult = mvc.perform(get("/api/csrf").with(anonymous())
                        .with(httpBasic("catalog", "integration-test-password")))
                .andExpect(status().isOk()).andReturn();
        var token = mapper.readTree(tokenResult.getResponse().getContentAsString());
        var session = (MockHttpSession) tokenResult.getRequest().getSession(false);
        mvc.perform(post("/api/products").session(session).with(anonymous())
                        .with(httpBasic("catalog", "integration-test-password"))
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X5\"}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select actor from audit_entries", String.class)).isEqualTo("catalog");
    }

    @Test
    void auditFailureRollsBackProductCreation() throws Exception {
        mvc.perform(post("/api/products").with(user("a".repeat(201))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X5\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
        assertThat(jdbc.queryForObject("select count(*) from products", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_entries", Integer.class)).isZero();
    }

    private String createProduct(String name) throws Exception {
        String result = mvc.perform(post("/api/products").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated()).andExpect(header().string("Location", startsWith("/api/products/")))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(result).get("id").asText();
    }

    private String createVariant(String product, String name, String price) throws Exception {
        String result = mvc.perform(post("/api/products/" + product + "/variants").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"sku\":\"shared-sku\",\"recommendedSalePrice\":" + price + "}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", startsWith("/api/product-variants/")))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(result).get("id").asText();
    }
}
