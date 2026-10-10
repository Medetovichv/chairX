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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@WithMockUser(username = "transfer-api-tester")
class InventoryTransferApiTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryAdjustmentService adjustments;

    UUID home;
    UUID office;
    UUID product;
    UUID variant;

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

        jdbc.update("""
                INSERT INTO products(id,name,active,created_at,updated_at)
                VALUES (?,'Transfer API test',true,now(),now())
                """, product);

        jdbc.update("""
                INSERT INTO product_variants(
                    id,product_id,name,recommended_sale_price,
                    active,created_at,updated_at
                )
                VALUES (?,?,'Test variant',10000,true,now(),now())
                """, variant, product);

        adjustments.recordValuedAdjustmentIn(
                UUID.randomUUID(), home, variant,
                5, new BigDecimal("500.00"), "fixture"
        );
    }

    @AfterEach
    void cleanup() {
        assertTestDatabase();
        clear();
        jdbc.update("DELETE FROM product_variants WHERE id=?", variant);
        jdbc.update("DELETE FROM products WHERE id=?", product);
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

    String request(UUID id, long quantity) {
        return """
                {
                  "transferId": "%s",
                  "sourceWarehouseId": "%s",
                  "destinationWarehouseId": "%s",
                  "variantId": "%s",
                  "quantity": %d
                }
                """.formatted(id, home, office, variant, quantity);
    }

    long count(String table) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table, Long.class
        );
    }

    @Test
    void createsTransferAndRecordsAuthenticatedActor() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/inventory/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(id, 3)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location", "/api/inventory/transfers/" + id))
                .andExpect(jsonPath("$.transferId").value(id.toString()))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.totalCost").value(300.00))
                .andExpect(jsonPath("$.outMovementId").isString())
                .andExpect(jsonPath("$.inMovementId").isString());

        assertThat(jdbc.queryForObject("""
                SELECT actor FROM inventory_transfers WHERE id=?
                """, String.class, id))
                .isEqualTo("transfer-api-tester");

        assertThat(count("inventory_transfers")).isEqualTo(1);
        assertThat(count("inventory_transfer_cost_origins")).isEqualTo(1);
    }

    @Test
    void rejectsAnonymousRequest() throws Exception {
        mvc.perform(post("/api/inventory/transfers")
                        .with(anonymous())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(UUID.randomUUID(), 1)))
                .andExpect(status().isUnauthorized());

        assertThat(count("inventory_transfers")).isZero();
    }

    @Test
    void rejectsMissingCsrfToken() throws Exception {
        mvc.perform(post("/api/inventory/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(UUID.randomUUID(), 1)))
                .andExpect(status().isForbidden());

        assertThat(count("inventory_transfers")).isZero();
    }

    @Test
    void rejectsInvalidQuantity() throws Exception {
        mvc.perform(post("/api/inventory/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(UUID.randomUUID(), 0)))
                .andExpect(status().isBadRequest());

        assertThat(count("inventory_transfers")).isZero();
    }

    @Test
    void rejectsInsufficientStock() throws Exception {
        mvc.perform(post("/api/inventory/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(UUID.randomUUID(), 6)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(count("inventory_transfers")).isZero();
    }

    @Test
    void rejectsConflictingReplay() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/inventory/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(id, 2)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/inventory/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(id, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("TRANSFER_OPERATION_CONFLICT"));

        assertThat(count("inventory_transfers")).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(3);
    }

    @Test
    void repeatedPostDoesNotMoveStockTwice() throws Exception {
        UUID id = UUID.randomUUID();

        String body = request(id, 3);

        mvc.perform(post("/api/inventory/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCost").value(300.00));

        mvc.perform(post("/api/inventory/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCost").value(300.00));

        assertThat(count("inventory_transfers")).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(3);
        assertThat(count("inventory_cost_allocations")).isEqualTo(1);

        assertThat(jdbc.queryForObject("""
                SELECT on_hand FROM inventory_balances
                WHERE warehouse_id=? AND product_variant_id=?
                """, Long.class, home, variant)).isEqualTo(2);

        assertThat(jdbc.queryForObject("""
                SELECT on_hand FROM inventory_balances
                WHERE warehouse_id=? AND product_variant_id=?
                """, Long.class, office, variant)).isEqualTo(3);
    }
}
