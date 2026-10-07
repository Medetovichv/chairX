package kg.chairx.delivery;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.api.RecordStockMovement;
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

import static kg.chairx.inventory.domain.StockMovementType.ADJUSTMENT_IN;
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
@WithMockUser(username = "delivery-api-tester")
class DeliveryApiTests {

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

    UUID home;
    UUID office;

    UUID product;
    UUID firstVariant;
    UUID secondVariant;
    UUID customer;

    @BeforeEach
    void fixture() {
        assertTestDatabase();
        clearData();

        home = jdbc.queryForObject(
                "select id from warehouses where code='HOME'",
                UUID.class
        );

        office = jdbc.queryForObject(
                "select id from warehouses where code='OFFICE'",
                UUID.class
        );

        product = UUID.randomUUID();
        firstVariant = UUID.randomUUID();
        secondVariant = UUID.randomUUID();
        customer = UUID.randomUUID();

        jdbc.update("""
                insert into products(
                    id,
                    name,
                    active,
                    created_at,
                    updated_at
                )
                values (?, 'Delivery API fixture', true, now(), now())
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
                values (?, ?, 'Black', 10000, true, now(), now())
                """, firstVariant, product);

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
                values (?, ?, 'White', 12000, true, now(), now())
                """, secondVariant, product);

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
                    'Delivery API Customer',
                    '+996555000000',
                    true,
                    now(),
                    now()
                )
                """, customer);

        addStock(home, firstVariant, 20);
        addStock(office, secondVariant, 20);
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
                "delete from product_variants where id in (?, ?)",
                firstVariant,
                secondVariant
        );

        jdbc.update(
                "delete from products where id=?",
                product
        );
    }

    @Test
    void createsAndGetsDeliveryThroughApi() throws Exception {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 2, "8500")
        );

        String body = mvc.perform(
                        post("/api/deliveries")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(deliveryJson(sale.id()))
                )
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                startsWith("/api/deliveries/")
                        )
                )
                .andExpect(jsonPath("$.saleId").value(sale.id().toString()))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.recipientName").value("Иван Иванов"))
                .andExpect(jsonPath("$.deliveryCost").value(300))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String deliveryId =
                mapper.readTree(body)
                        .get("id")
                        .asText();

        mvc.perform(
                        get("/api/deliveries/" + deliveryId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deliveryId))
                .andExpect(jsonPath("$.saleId").value(sale.id().toString()))
                .andExpect(jsonPath("$.status").value("READY"));

        assertThat(
                jdbc.queryForObject(
                        "select count(*) from deliveries where sale_id=?",
                        Integer.class,
                        sale.id()
                )
        ).isEqualTo(1);
    }

    @Test
    void dispatchAndDeliverWorkThroughApi() throws Exception {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 2, "8500")
        );

        String deliveryId = createDelivery(sale.id());

        mvc.perform(
                        post("/api/deliveries/" + deliveryId + "/dispatch")
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.dispatchedAt").isString());

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).onHand()
        ).isEqualTo(18);

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).reserved()
        ).isZero();

        mvc.perform(
                        post("/api/deliveries/" + deliveryId + "/deliver")
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").isString());

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from stock_movements
                        where movement_type='SALE_OUT'
                        """,
                        Integer.class
                )
        ).isEqualTo(1);
    }

    @Test
    void failedDeliveryCanReturnToWarehouseThroughApi() throws Exception {
        var sale = createSale(
                FulfillmentType.REGION_DELIVERY,
                item(firstVariant, home, 3, "8500")
        );

        String deliveryId = createDelivery(sale.id());

        mvc.perform(
                        post("/api/deliveries/" + deliveryId + "/dispatch")
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        mvc.perform(
                        post("/api/deliveries/" + deliveryId + "/fail")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "reason": "Клиент отказался"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(
                        jsonPath("$.failureReason")
                                .value("Клиент отказался")
                );

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).onHand()
        ).isEqualTo(17);

        mvc.perform(
                        post(
                                "/api/deliveries/"
                                        + deliveryId
                                        + "/return-to-warehouse"
                        )
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "warehouseId": "%s"
                                        }
                                        """.formatted(home)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.returnedToWarehouseAt").isString())
                .andExpect(
                        jsonPath("$.returnWarehouseId")
                                .value(home.toString())
                );

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).onHand()
        ).isEqualTo(20);

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from stock_movements
                        where movement_type='RETURN_IN'
                          and source_type='DELIVERY_RETURN'
                        """,
                        Integer.class
                )
        ).isEqualTo(1);
    }

    @Test
    void cancellingReadyDeliveryWorksThroughApi() throws Exception {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 2, "8500")
        );

        String deliveryId = createDelivery(sale.id());

        mvc.perform(
                        post("/api/deliveries/" + deliveryId + "/cancel")
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isString());

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).onHand()
        ).isEqualTo(20);

        assertThat(
                inventory.getBalance(
                        home,
                        firstVariant
                ).reserved()
        ).isZero();

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from stock_movements
                        where movement_type='SALE_OUT'
                        """,
                        Integer.class
                )
        ).isZero();
    }

    @Test
    void duplicateDeliveryReturnsBusinessConflict() throws Exception {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 1, "8500")
        );

        createDelivery(sale.id());

        mvc.perform(
                        post("/api/deliveries")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(deliveryJson(sale.id()))
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value("DELIVERY_ALREADY_EXISTS")
                );

        assertThat(
                jdbc.queryForObject(
                        "select count(*) from deliveries where sale_id=?",
                        Integer.class,
                        sale.id()
                )
        ).isEqualTo(1);
    }

    @Test
    void invalidStateReturnsBusinessConflict() throws Exception {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 1, "8500")
        );

        String deliveryId = createDelivery(sale.id());

        mvc.perform(
                        post("/api/deliveries/" + deliveryId + "/deliver")
                                .with(csrf())
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_DELIVERY_STATUS")
                );
    }

    @Test
    void missingDeliveryUsesErrorContract() throws Exception {
        mvc.perform(
                        get("/api/deliveries/" + UUID.randomUUID())
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value("DELIVERY_NOT_FOUND")
                );
    }

    @Test
    void malformedDeliveryIdUsesErrorContract() throws Exception {
        mvc.perform(
                        get("/api/deliveries/not-a-uuid")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                );
    }

    @Test
    void malformedJsonUsesErrorContract() throws Exception {
        mvc.perform(
                        post("/api/deliveries")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                );
    }

    @Test
    void invalidCreateRequestUsesValidationContract() throws Exception {
        mvc.perform(
                        post("/api/deliveries")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                )
                .andExpect(
                        jsonPath("$.details.fields").isMap()
                );
    }

    @Test
    void invalidFailureRequestUsesValidationContract() throws Exception {
        var sale = createSale(
                FulfillmentType.REGION_DELIVERY,
                item(firstVariant, home, 1, "8500")
        );

        String deliveryId = createDelivery(sale.id());

        mvc.perform(
                        post("/api/deliveries/" + deliveryId + "/dispatch")
                                .with(csrf())
                )
                .andExpect(status().isOk());

        mvc.perform(
                        post("/api/deliveries/" + deliveryId + "/fail")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "reason": "   "
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                );
    }

    @Test
    void deliveryEndpointsKeepSecurityAndCsrfProtection() throws Exception {
        var sale = createSale(
                FulfillmentType.CITY_DELIVERY,
                item(firstVariant, home, 1, "8500")
        );

        mvc.perform(
                        post("/api/deliveries")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(deliveryJson(sale.id()))
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACCESS_DENIED")
                );

        mvc.perform(
                        get("/api/deliveries/" + UUID.randomUUID())
                                .with(anonymous())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("AUTHENTICATION_REQUIRED")
                );
    }

    private String createDelivery(
            UUID saleId
    ) throws Exception {
        String body = mvc.perform(
                        post("/api/deliveries")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(deliveryJson(saleId))
                )
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                startsWith("/api/deliveries/")
                        )
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        return mapper.readTree(body)
                .get("id")
                .asText();
    }

    private String deliveryJson(
            UUID saleId
    ) {
        return """
                {
                  "saleId": "%s",
                  "recipientName": "Иван Иванов",
                  "recipientPhone": "+996555111222",
                  "address": "Бишкек, ул. Тестовая 10",
                  "cityRegion": "Бишкек",
                  "deliveryCost": 300,
                  "carrierName": "ChairX Courier",
                  "trackingNumber": null,
                  "comment": "Позвонить перед доставкой"
                }
                """.formatted(saleId);
    }

    private kg.chairx.sale.api.SaleResponse createSale(
            FulfillmentType fulfillmentType,
            CreateSaleItemRequest... items
    ) {
        return sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customer,
                        fulfillmentType,
                        List.of(items)
                )
        );
    }

    private CreateSaleItemRequest item(
            UUID variantId,
            UUID warehouseId,
            long quantity,
            String unitSalePrice
    ) {
        return new CreateSaleItemRequest(
                variantId,
                warehouseId,
                quantity,
                new BigDecimal(unitSalePrice)
        );
    }

    private void addStock(
            UUID warehouseId,
            UUID variantId,
            long quantity
    ) {
        inventory.recordMovement(
                new RecordStockMovement(
                        UUID.randomUUID(),
                        warehouseId,
                        variantId,
                        ADJUSTMENT_IN,
                        quantity,
                        "DELIVERY_API_TEST_FIXTURE",
                        UUID.randomUUID(),
                        "delivery-api-test"
                )
        );
    }

    private void clearData() {
        assertTestDatabase();

        jdbc.execute("""
                truncate table
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
                    'SALE',
                    'DELIVERY',
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