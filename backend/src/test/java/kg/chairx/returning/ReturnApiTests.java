package kg.chairx.returning;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.cost.InventoryAdjustmentService;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.sale.api.CreateSaleItemRequest;
import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.domain.FulfillmentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@WithMockUser(username = "return-api-tester")
class ReturnApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SaleService sales;

    @Autowired
    InventoryService inventory;

    @Autowired
    InventoryAdjustmentService adjustments;

    UUID home;
    UUID product;
    UUID variant;
    UUID customer;

    @BeforeEach
    void fixture() {
        assertTestDatabase();
        clearData();

        home = jdbc.queryForObject(
                "select id from warehouses where code='HOME'",
                UUID.class
        );

        product = UUID.randomUUID();
        variant = UUID.randomUUID();
        customer = UUID.randomUUID();

        jdbc.update("""
                insert into products(
                    id,
                    name,
                    active,
                    created_at,
                    updated_at
                )
                values (?, 'Return API fixture', true, now(), now())
                """, product);

        jdbc.update("""
                insert into product_variants(
                    id,
                    product_id,
                    name,
                    recommended_sale_price,
                    active,
                    created_at,
                    updated_at
                )
                values (?, ?, 'Black', 8500, true, now(), now())
                """, variant, product);

        jdbc.update("""
                insert into customers(
                    id,
                    full_name,
                    phone,
                    active,
                    created_at,
                    updated_at
                )
                values (
                    ?,
                    'Return API Customer',
                    '+996555000002',
                    true,
                    now(),
                    now()
                )
                """, customer);

        adjustments.recordValuedAdjustmentIn(
                UUID.randomUUID(),
                home,
                variant,
                20,
                BigDecimal.valueOf(5000).multiply(BigDecimal.valueOf(20)),
                "return-api-test"
        );
    }

    @AfterEach
    void cleanup() {
        assertTestDatabase();

        clearData();

        jdbc.update(
                "delete from customers where id=?",
                customer
        );

        jdbc.update(
                "delete from product_variants where id=?",
                variant
        );

        jdbc.update(
                "delete from products where id=?",
                product
        );
    }

    @Test
    void createsAndGetsReturnThroughApi()
            throws Exception {

        var sale = createFulfilledSale(
                2,
                "8500"
        );

        UUID saleItemId =
                sale.items().getFirst().id();

        UUID idempotencyKey =
                UUID.randomUUID();

        String body = mvc.perform(
                        post("/api/returns")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        returnJson(
                                                sale.id(),
                                                home,
                                                idempotencyKey,
                                                saleItemId,
                                                1,
                                                "SELLABLE"
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                startsWith("/api/returns/")
                        )
                )
                .andExpect(
                        jsonPath("$.saleId")
                                .value(sale.id().toString())
                )
                .andExpect(
                        jsonPath("$.warehouseId")
                                .value(home.toString())
                )
                .andExpect(
                        jsonPath("$.items[0].saleItemId")
                                .value(saleItemId.toString())
                )
                .andExpect(
                        jsonPath("$.items[0].quantity")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.items[0].condition")
                                .value("SELLABLE")
                )
                .andExpect(
                        jsonPath("$.reason")
                                .value("Возврат клиента")
                )
                .andExpect(
                        jsonPath("$.comment")
                                .value("Return API test")
                )
                .andExpect(
                        jsonPath("$.createdBy")
                                .value("return-api-tester")
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        String returnId =
                mapper.readTree(body)
                        .get("id")
                        .asText();

        mvc.perform(
                        get(
                                "/api/returns/"
                                        + returnId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(returnId)
                )
                .andExpect(
                        jsonPath("$.saleId")
                                .value(sale.id().toString())
                )
                .andExpect(
                        jsonPath("$.warehouseId")
                                .value(home.toString())
                )
                .andExpect(
                        jsonPath("$.items[0].saleItemId")
                                .value(saleItemId.toString())
                )
                .andExpect(
                        jsonPath("$.items[0].quantity")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.items[0].condition")
                                .value("SELLABLE")
                );

        var balance =
                inventory.getBalance(
                        home,
                        variant
                );

        /*
         * 20 initial
         * -2 SALE_OUT
         * +1 RETURN_IN
         * =19
         */
        assertThat(balance.onHand())
                .isEqualTo(19);

        assertThat(balance.blocked())
                .isZero();

        assertThat(balance.available())
                .isEqualTo(19);
    }

    @Test
    void blockedReturnIsExposedThroughApiAndBlocksStock()
            throws Exception {

        var sale = createFulfilledSale(
                1,
                "8500"
        );

        UUID saleItemId =
                sale.items().getFirst().id();

        mvc.perform(
                        post("/api/returns")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        returnJson(
                                                sale.id(),
                                                home,
                                                UUID.randomUUID(),
                                                saleItemId,
                                                1,
                                                "BLOCKED"
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.items[0].condition")
                                .value("BLOCKED")
                );

        var balance =
                inventory.getBalance(
                        home,
                        variant
                );

        assertThat(balance.onHand())
                .isEqualTo(20);

        assertThat(balance.blocked())
                .isEqualTo(1);

        assertThat(balance.available())
                .isEqualTo(19);
    }

    @Test
    void returnAboveRemainingQuantityUsesBusinessConflict()
            throws Exception {

        var sale = createFulfilledSale(
                1,
                "8500"
        );

        UUID saleItemId =
                sale.items().getFirst().id();

        mvc.perform(
                        post("/api/returns")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        returnJson(
                                                sale.id(),
                                                home,
                                                UUID.randomUUID(),
                                                saleItemId,
                                                1,
                                                "SELLABLE"
                                        )
                                )
                )
                .andExpect(status().isCreated());

        mvc.perform(
                        post("/api/returns")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        returnJson(
                                                sale.id(),
                                                home,
                                                UUID.randomUUID(),
                                                saleItemId,
                                                1,
                                                "SELLABLE"
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "RETURN_QUANTITY_EXCEEDED"
                                )
                );

        assertThat(
                jdbc.queryForObject(
                        """
                        select coalesce(sum(quantity), 0)
                        from return_items
                        where sale_item_id=?
                        """,
                        Long.class,
                        saleItemId
                )
        ).isEqualTo(1);
    }

    @Test
    void confirmedSaleCannotBeReturnedThroughApi()
            throws Exception {

        var sale = createSale(
                1,
                "8500"
        );

        UUID saleItemId =
                sale.items().getFirst().id();

        mvc.perform(
                        post("/api/returns")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        returnJson(
                                                sale.id(),
                                                home,
                                                UUID.randomUUID(),
                                                saleItemId,
                                                1,
                                                "SELLABLE"
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "SALE_NOT_FULFILLED"
                                )
                );
    }

    @Test
    void repeatedIdempotentRequestReturnsSameResource()
            throws Exception {

        var sale = createFulfilledSale(
                1,
                "8500"
        );

        UUID saleItemId =
                sale.items().getFirst().id();

        UUID idempotencyKey =
                UUID.randomUUID();

        String request =
                returnJson(
                        sale.id(),
                        home,
                        idempotencyKey,
                        saleItemId,
                        1,
                        "SELLABLE"
                );

        String firstBody = mvc.perform(
                        post("/api/returns")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(request)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstId =
                mapper.readTree(firstBody)
                        .get("id")
                        .asText();

        String secondBody = mvc.perform(
                        post("/api/returns")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(request)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                "/api/returns/" + firstId
                        )
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondId =
                mapper.readTree(secondBody)
                        .get("id")
                        .asText();

        assertThat(secondId)
                .isEqualTo(firstId);

        assertThat(
                jdbc.queryForObject(
                        "select count(*) from returns",
                        Integer.class
                )
        ).isEqualTo(1);

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from stock_movements
                        where movement_type='RETURN_IN'
                          and source_type='SALE_RETURN'
                        """,
                        Integer.class
                )
        ).isEqualTo(1);
    }

    @Test
    void reusedIdempotencyKeyWithDifferentPayloadUsesBusinessConflict()
            throws Exception {

        var sale = createFulfilledSale(
                2,
                "8500"
        );

        UUID saleItemId =
                sale.items().getFirst().id();

        UUID idempotencyKey =
                UUID.randomUUID();

        mvc.perform(
                        post("/api/returns")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        returnJson(
                                                sale.id(),
                                                home,
                                                idempotencyKey,
                                                saleItemId,
                                                1,
                                                "SELLABLE"
                                        )
                                )
                )
                .andExpect(status().isCreated());

        mvc.perform(
                        post("/api/returns")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        returnJson(
                                                sale.id(),
                                                home,
                                                idempotencyKey,
                                                saleItemId,
                                                2,
                                                "SELLABLE"
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "IDEMPOTENCY_KEY_REUSED"
                                )
                );
    }

    @Test
    void missingReturnUsesErrorContract()
            throws Exception {

        mvc.perform(
                        get(
                                "/api/returns/"
                                        + UUID.randomUUID()
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value("RETURN_NOT_FOUND")
                );
    }

    @Test
    void invalidCreateRequestUsesValidationContract()
            throws Exception {

        mvc.perform(
                        post("/api/returns")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                )
                .andExpect(
                        jsonPath("$.details.fields")
                                .isMap()
                );
    }

    @Test
    void invalidReturnQuantityUsesValidationContract()
            throws Exception {

        var sale = createFulfilledSale(
                1,
                "8500"
        );

        UUID saleItemId =
                sale.items().getFirst().id();

        mvc.perform(
                        post("/api/returns")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        returnJson(
                                                sale.id(),
                                                home,
                                                UUID.randomUUID(),
                                                saleItemId,
                                                0,
                                                "SELLABLE"
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                );
    }

    @Test
    void malformedReturnIdUsesErrorContract()
            throws Exception {

        mvc.perform(
                        get(
                                "/api/returns/not-a-uuid"
                        )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                );
    }

    @Test
    void malformedJsonUsesErrorContract()
            throws Exception {

        mvc.perform(
                        post("/api/returns")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                );
    }

    @Test
    void returnEndpointsKeepSecurityAndCsrfProtection()
            throws Exception {

        var sale = createFulfilledSale(
                1,
                "8500"
        );

        UUID saleItemId =
                sale.items().getFirst().id();

        mvc.perform(
                        post("/api/returns")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        returnJson(
                                                sale.id(),
                                                home,
                                                UUID.randomUUID(),
                                                saleItemId,
                                                1,
                                                "SELLABLE"
                                        )
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACCESS_DENIED")
                );

        mvc.perform(
                        get(
                                "/api/returns/"
                                        + UUID.randomUUID()
                        )
                                .with(anonymous())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "AUTHENTICATION_REQUIRED"
                                )
                );
    }

    private String returnJson(
            UUID saleId,
            UUID warehouseId,
            UUID idempotencyKey,
            UUID saleItemId,
            long quantity,
            String condition
    ) {
        return """
                {
                  "saleId": "%s",
                  "warehouseId": "%s",
                  "idempotencyKey": "%s",
                  "items": [
                    {
                      "saleItemId": "%s",
                      "quantity": %d,
                      "condition": "%s"
                    }
                  ],
                  "reason": "Возврат клиента",
                  "comment": "Return API test"
                }
                """.formatted(
                saleId,
                warehouseId,
                idempotencyKey,
                saleItemId,
                quantity,
                condition
        );
    }

    private kg.chairx.sale.api.SaleResponse createFulfilledSale(
            long quantity,
            String unitSalePrice
    ) {
        var sale = createSale(
                quantity,
                unitSalePrice
        );

        return sales.fulfill(
                sale.id()
        );
    }

    private kg.chairx.sale.api.SaleResponse createSale(
            long quantity,
            String unitSalePrice
    ) {
        return sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customer,
                        FulfillmentType.SELF_PICKUP,
                        List.of(
                                new CreateSaleItemRequest(
                                        variant,
                                        home,
                                        quantity,
                                        new BigDecimal(
                                                unitSalePrice
                                        )
                                )
                        )
                )
        );
    }

    private void clearData() {
        assertTestDatabase();

        jdbc.execute("""
                truncate table
                    inventory_cost_movements, inventory_cost_allocations, inventory_cost_layers, exchange_settlements, exchanges, refunds,
                    return_items,
                    returns,
                    payments,
                    deliveries,
                    sale_items,
                    sales,
                    stock_movements,
                    inventory_balances
                restart identity
                """);

        jdbc.execute(
                "alter sequence sale_number_seq restart with 1"
        );

        jdbc.update("""
                delete from audit_entries
                where entity_type in (
                    'RETURN',
                    'PAYMENT',
                    'DELIVERY',
                    'SALE',
                    'STOCK_MOVEMENT'
                )
                """);
    }

    private void assertTestDatabase() {
        String database = jdbc.queryForObject(
                "select current_database()",
                String.class
        );

        assertThat(database)
                .isEqualTo("chairx_test");
    }
}