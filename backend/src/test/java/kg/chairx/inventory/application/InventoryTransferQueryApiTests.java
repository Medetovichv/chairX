package kg.chairx.inventory.application;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.cost.InventoryAdjustmentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@WithMockUser(username = "transfer-query-tester")
class InventoryTransferQueryApiTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryAdjustmentService adjustments;
    @Autowired InventoryTransferService transfers;

    UUID home;
    UUID office;
    UUID product;
    UUID variant;
    UUID secondVariant;

    @BeforeEach
    void setup() {
        assertTestDatabase();
        clear();

        home = jdbc.queryForObject(
                "SELECT id FROM warehouses WHERE code='HOME'",
                UUID.class
        );

        office = jdbc.queryForObject(
                "SELECT id FROM warehouses WHERE code='OFFICE'",
                UUID.class
        );

        product = UUID.randomUUID();
        variant = UUID.randomUUID();
        secondVariant = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO products(id,name,active,created_at,updated_at)
                VALUES (?,'Transfer query test',true,now(),now())
                """, product);

        jdbc.update("""
                INSERT INTO product_variants(
                    id,product_id,name,recommended_sale_price,
                    active,created_at,updated_at
                )
                VALUES
                    (?,?,'First',10000,true,now(),now()),
                    (?,?,'Second',12000,true,now(),now())
                """,
                variant, product,
                secondVariant, product
        );

        stock(variant, 20, "2000.00");
        stock(secondVariant, 20, "4000.00");
    }

    @AfterEach
    void cleanup() {
        assertTestDatabase();
        clear();

        jdbc.update(
                "DELETE FROM product_variants WHERE id IN (?,?)",
                variant, secondVariant
        );

        jdbc.update(
                "DELETE FROM products WHERE id=?",
                product
        );
    }

    void assertTestDatabase() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()", String.class
        )).isEqualTo("chairx_test");
    }

    void clear() {
        jdbc.execute("""
                TRUNCATE TABLE
                    inventory_transfer_cost_origins,
                    inventory_transfers,
                    inventory_cost_restorations,
                    inventory_cost_write_offs,
                    inventory_cost_allocations,
                    inventory_cost_movements,
                    inventory_cost_layers,
                    exchange_settlements,
                    exchanges,
                    refunds,
                    return_items,
                    returns,
                    payments,
                    deliveries,
                    sale_items,
                    sales,
                    defects,
                    purchase_payments,
                    purchase_receipt_items,
                    purchase_receipts,
                    purchase_items,
                    purchases,
                    stock_movements,
                    inventory_balances
                """);

        jdbc.update("DELETE FROM audit_entries");
    }

    void stock(UUID v, long quantity, String cost) {
        adjustments.recordValuedAdjustmentIn(
                UUID.randomUUID(),
                home,
                v,
                quantity,
                new BigDecimal(cost),
                "fixture"
        );
    }

    UUID transfer(UUID v, long quantity) {
        UUID id = UUID.randomUUID();

        transfers.transfer(
                id,
                home,
                office,
                v,
                quantity,
                "transfer-query-tester"
        );

        return id;
    }

    @Test
    void getsTransferById() throws Exception {
        UUID id = transfer(variant, 3);

        mvc.perform(get("/api/inventory/transfers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.sourceWarehouseId")
                        .value(home.toString()))
                .andExpect(jsonPath("$.destinationWarehouseId")
                        .value(office.toString()))
                .andExpect(jsonPath("$.variantId")
                        .value(variant.toString()))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.totalCost").value(300.00))
                .andExpect(jsonPath("$.actor")
                        .value("transfer-query-tester"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void missingTransferReturns404() throws Exception {
        mvc.perform(get(
                        "/api/inventory/transfers/{id}",
                        UUID.randomUUID()
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("INVENTORY_TRANSFER_NOT_FOUND"));
    }

    @Test
    void filtersByVariant() throws Exception {
        UUID first = transfer(variant, 2);
        transfer(secondVariant, 3);

        mvc.perform(get("/api/inventory/transfers")
                        .param("variantId", variant.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id")
                        .value(first.toString()));
    }

    @Test
    void filtersByWarehouse() throws Exception {
        transfer(variant, 2);

        mvc.perform(get("/api/inventory/transfers")
                        .param("warehouseId", home.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/inventory/transfers")
                        .param("warehouseId", office.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/inventory/transfers")
                        .param("warehouseId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void paginatesResults() throws Exception {
        transfer(variant, 1);
        transfer(variant, 1);
        transfer(variant, 1);

        mvc.perform(get("/api/inventory/transfers")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));

        mvc.perform(get("/api/inventory/transfers")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void sortsNewestFirst() throws Exception {
        UUID first = transfer(variant, 1);
        UUID second = transfer(variant, 1);

        mvc.perform(get("/api/inventory/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id")
                        .value(second.toString()))
                .andExpect(jsonPath("$.items[1].id")
                        .value(first.toString()));
    }

    @Test
    void filtersByDateRange() throws Exception {
        UUID id = transfer(variant, 2);

        OffsetDateTime created = jdbc.queryForObject(
                "SELECT created_at FROM inventory_transfers WHERE id=?",
                (rs, row) ->
                        rs.getObject(1, OffsetDateTime.class),
                id
        );

        mvc.perform(get("/api/inventory/transfers")
                        .param("from", created.minusDays(1).toString())
                        .param("to", created.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/inventory/transfers")
                        .param("from", created.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsInvalidDateRange() throws Exception {
        mvc.perform(get("/api/inventory/transfers")
                        .param("from", "2026-10-10T00:00:00Z")
                        .param("to", "2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_TRANSFER_QUERY"));
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        mvc.perform(get("/api/inventory/transfers")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/inventory/transfers")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousCannotReadHistory() throws Exception {
        mvc.perform(get("/api/inventory/transfers")
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
    }
}
