package kg.chairx.warehouse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import kg.chairx.PostgresTestConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
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
@WithMockUser(username = "warehouse-tester")
class WarehouseTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired DataSource dataSource;

    @BeforeEach
    void clearOnlyWarehouseTestData() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("chairx_test");
        jdbc.update("delete from audit_entries where entity_type = 'WAREHOUSE'");
        jdbc.update("delete from warehouses where code not in ('HOME','OFFICE')");
    }

    @Test
    void initialWarehousesComeFromMigration() throws Exception {
        mvc.perform(get("/api/warehouses"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].code").value("HOME"))
                .andExpect(jsonPath("$.items[0].name").value("Домашний склад"))
                .andExpect(jsonPath("$.items[1].code").value("OFFICE"))
                .andExpect(jsonPath("$.items[1].name").value("Офисный склад"))
                .andExpect(jsonPath("$.items[*].active", everyItem(is(true))));
    }

    @Test
    void lifecyclePreservesCodeAndCreationTimeAndAuditsChanges() throws Exception {
        String id = create("BRANCH");
        var initial = mapper.readTree(mvc.perform(get("/api/warehouses/" + id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(put("/api/warehouses/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Новый склад \",\"address\":\" Бишкек \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Новый склад"))
                .andExpect(jsonPath("$.address").value("Бишкек"))
                .andExpect(jsonPath("$.code").value("BRANCH"))
                .andExpect(jsonPath("$.createdAt").value(initial.get("createdAt").asText()));
        mvc.perform(post("/api/warehouses/" + id + "/deactivate").with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/api/warehouses/" + id + "/deactivate").with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/warehouses/" + id + "/activate").with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(post("/api/warehouses/" + id + "/activate").with(csrf())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from audit_entries where entity_type='WAREHOUSE'", Integer.class))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("select before_state->>'name' from audit_entries where entity_type='WAREHOUSE' and action='UPDATED'", String.class))
                .isEqualTo("Склад");
        mvc.perform(put("/api/warehouses/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Склад\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address").isEmpty());
    }

    @Test
    void codeCannotBeChangedThroughUpdateAndDeleteIsUnavailable() throws Exception {
        String id = create("BRANCH");
        mvc.perform(put("/api/warehouses/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Склад\",\"code\":\"NEW_CODE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(delete("/api/warehouses/" + id).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/warehouses/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BRANCH"));
    }

    @Test
    void duplicateCodeIsRejectedAlsoForInactiveWarehouse() throws Exception {
        String id = create("BRANCH");
        mvc.perform(post("/api/warehouses/" + id + "/deactivate").with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/warehouses").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("BRANCH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_CODE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Склад с таким кодом уже существует"));
        assertThat(jdbc.queryForObject("select count(*) from warehouses where code='BRANCH'", Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "home", " HOME", "HOME ", "Склад", "WH/1", "_WH"})
    void invalidCodesAreRejected(String code) throws Exception {
        mvc.perform(post("/api/warehouses").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(code))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fields.code").isArray());
    }

    @Test
    void missingFieldsAndLengthLimitsAreValidated() throws Exception {
        for (String body : List.of("{}", "{\"name\":\" \",\"code\":\"WH\"}",
                mapper.writeValueAsString(Map.of("name", "a".repeat(201), "code", "WH")),
                mapper.writeValueAsString(Map.of("name", "Склад", "code", "A".repeat(51))),
                mapper.writeValueAsString(Map.of("name", "Склад", "code", "WH", "address", "a".repeat(1001))))) {
            mvc.perform(post("/api/warehouses").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void validationAppliesToUpdatesAndListParameters() throws Exception {
        String id = create("BRANCH");
        mvc.perform(put("/api/warehouses/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        for (String query : List.of("page=-1", "size=0", "size=101")) {
            mvc.perform(get("/api/warehouses?" + query)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/warehouses?size=1&page=0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void missingWarehousesReturnRussian404() throws Exception {
        String path = "/api/warehouses/" + UUID.randomUUID();
        mvc.perform(get(path)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Склад не найден"));
        mvc.perform(post(path + "/activate").with(csrf())).andExpect(status().isNotFound());
        mvc.perform(put(path).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Склад\"}")).andExpect(status().isNotFound());
    }

    @Test
    void authenticationAndCsrfAreRequired() throws Exception {
        mvc.perform(get("/api/warehouses").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/warehouses").contentType(MediaType.APPLICATION_JSON).content(body("WH")))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditFailureRollsBackCreation() throws Exception {
        mvc.perform(post("/api/warehouses").with(user("a".repeat(201))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("ROLLBACK")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from warehouses where code='ROLLBACK'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_entries where entity_type='WAREHOUSE'", Integer.class)).isZero();
    }

    @Test
    void databaseEnforcesUniqueAndValidCodeAndRequiredName() {
        for (String code : List.of("HOME", "bad", " ")) {
            assertThatThrownBy(() -> jdbc.update("""
                    insert into warehouses(id,name,code,active,created_at,updated_at)
                    values (?, 'Склад', ?, true, now(), now())
                    """, UUID.randomUUID(), code)).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("""
                insert into warehouses(id,name,code,active,created_at,updated_at)
                values (?, null, 'INVALID', true, now(), now())
                """, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentCreatesWithSameCodeProduceOneWarehouseAndOneAudit() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> create = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return mvc.perform(post("/api/warehouses").with(user("worker")).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON).content(body("RACE")))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(create);
            var second = executor.submit(create);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from warehouses where code='RACE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_entries where entity_type='WAREHOUSE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void migrationUpgradesV1PreservesProductAndDoesNotReseedEditedWarehouses() {
        String schema = "warehouse_upgrade_test";
        try {
            var baseline = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("1").load();
            baseline.migrate();
            UUID product = UUID.randomUUID();
            jdbc.update("""
                    insert into warehouse_upgrade_test.products(id,name,active,created_at,updated_at)
                    values (?, 'Existing product', true, now(), now())
                    """, product);
            var upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("select name from warehouse_upgrade_test.products where id=?", String.class, product))
                    .isEqualTo("Existing product");
            UUID home = jdbc.queryForObject("select id from warehouse_upgrade_test.warehouses where code='HOME'", UUID.class);
            jdbc.update("update warehouse_upgrade_test.warehouses set name='Изменён', active=false where code='HOME'");
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
            upgrade.validate();
            assertThat(jdbc.queryForObject("select count(*) from warehouse_upgrade_test.warehouses", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select id from warehouse_upgrade_test.warehouses where code='HOME'", UUID.class)).isEqualTo(home);
            assertThat(jdbc.queryForObject("select name from warehouse_upgrade_test.warehouses where code='HOME'", String.class)).isEqualTo("Изменён");
            assertThat(jdbc.queryForObject("select active from warehouse_upgrade_test.warehouses where code='HOME'", Boolean.class)).isFalse();
        } finally {
            jdbc.execute("drop schema if exists warehouse_upgrade_test cascade");
        }
    }

    private String create(String code) throws Exception {
        var result = mvc.perform(post("/api/warehouses").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(code))).andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/warehouses/")))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(result).get("id").asText();
    }

    private String body(String code) {
        return mapper.writeValueAsString(Map.of("name", "Склад", "code", code));
    }
}
