package kg.chairx.payment;

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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.junit.jupiter.api.extension.ExtendWith(kg.chairx.FundedFinanceExtension.class)
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@WithMockUser(username = "payment-api-tester", authorities = {"PAYMENTS_READ","PAYMENTS_CREATE","PAYMENTS_CANCEL"})
class PaymentApiTests {

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
                values (?, 'Payment API fixture', true, now(), now())
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
                    'Payment API Customer',
                    '+996555000000',
                    true,
                    now(),
                    now()
                )
                """, customer);

        inventory.recordMovement(
                new RecordStockMovement(
                        UUID.randomUUID(),
                        home,
                        variant,
                        ADJUSTMENT_IN,
                        20,
                        "PAYMENT_API_TEST_FIXTURE",
                        UUID.randomUUID(),
                        "payment-api-test"
                )
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
    void createsAndGetsPaymentThroughApi()
            throws Exception {

        var sale = createSale(
                2,
                "8500"
        );

        String body = mvc.perform(
                        post("/api/payments")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        paymentJson(
                                                sale.id(),
                                                "TRANSFER"
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                startsWith("/api/payments/")
                        )
                )
                .andExpect(
                        jsonPath("$.saleId")
                                .value(sale.id().toString())
                )
                .andExpect(
                        jsonPath("$.amount")
                                .value(17000)
                )
                .andExpect(
                        jsonPath("$.method")
                                .value("TRANSFER")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PAID")
                )
                .andExpect(
                        jsonPath("$.paidBy")
                                .value("payment-api-tester")
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        String paymentId =
                mapper.readTree(body)
                        .get("id")
                        .asText();

        mvc.perform(
                        get(
                                "/api/payments/"
                                        + paymentId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(paymentId)
                )
                .andExpect(
                        jsonPath("$.saleId")
                                .value(sale.id().toString())
                )
                .andExpect(
                        jsonPath("$.amount")
                                .value(17000)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PAID")
                );
    }

    @Test
    void amountCannotBeSuppliedByClient()
            throws Exception {

        var sale = createSale(
                1,
                "8500"
        );

        mvc.perform(
                        post("/api/payments")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "saleId": "%s",
                                          "method": "CASH",
                                          "amount": 1
                                        }
                                        """.formatted(
                                                sale.id()
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                );

        assertThat(
                jdbc.queryForObject(
                        "select count(*) from payments",
                        Integer.class
                )
        ).isZero();
    }

    @Test
    void duplicateActivePaymentReturnsBusinessConflict()
            throws Exception {

        var sale = createSale(
                1,
                "8500"
        );

        createPayment(
                sale.id(),
                "TRANSFER"
        );

        mvc.perform(
                        post("/api/payments")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        paymentJson(
                                                sale.id(),
                                                "CASH"
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value("SALE_ALREADY_PAID")
                );
    }

    @Test
    void cancelledPaymentAllowsNewPaymentAndKeepsHistory()
            throws Exception {

        var sale = createSale(
                1,
                "8500"
        );

        String firstPaymentId =
                createPayment(
                        sale.id(),
                        "TRANSFER"
                );

        mvc.perform(
                        post(
                                "/api/payments/"
                                        + firstPaymentId
                                        + "/cancel"
                        )
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "reason": "Ошибочная регистрация оплаты"
                                        }
                                        """
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("CANCELLED")
                )
                .andExpect(
                        jsonPath("$.cancellationReason")
                                .value(
                                        "Ошибочная регистрация оплаты"
                                )
                );

        String secondPaymentId =
                createPayment(
                        sale.id(),
                        "CASH"
                );

        mvc.perform(
                        get(
                                "/api/payments/sale/"
                                        + sale.id()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$", hasSize(2))
                )
                .andExpect(
                        jsonPath("$[0].id")
                                .value(firstPaymentId)
                )
                .andExpect(
                        jsonPath("$[0].status")
                                .value("CANCELLED")
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(secondPaymentId)
                )
                .andExpect(
                        jsonPath("$[1].status")
                                .value("PAID")
                );
    }

    @Test
    void activePaymentCanBeRetrievedThroughApi()
            throws Exception {

        var sale = createSale(
                1,
                "8500"
        );

        String paymentId =
                createPayment(
                        sale.id(),
                        "TRANSFER"
                );

        mvc.perform(
                        get(
                                "/api/payments/sale/"
                                        + sale.id()
                                        + "/active"
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(paymentId)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PAID")
                );
    }

    @Test
    void cancelledSaleCannotBePaidThroughApi()
            throws Exception {

        var sale = createSale(
                1,
                "8500"
        );

        sales.cancel(sale.id());

        mvc.perform(
                        post("/api/payments")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        paymentJson(
                                                sale.id(),
                                                "CASH"
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value("SALE_CANCELLED")
                );
    }

    @Test
    void missingPaymentUsesErrorContract()
            throws Exception {

        mvc.perform(
                        get(
                                "/api/payments/"
                                        + UUID.randomUUID()
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value("PAYMENT_NOT_FOUND")
                );
    }

    @Test
    void invalidCreateRequestUsesValidationContract()
            throws Exception {

        mvc.perform(
                        post("/api/payments")
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
    void invalidCancelRequestUsesValidationContract()
            throws Exception {

        var sale = createSale(
                1,
                "8500"
        );

        String paymentId =
                createPayment(
                        sale.id(),
                        "CASH"
                );

        mvc.perform(
                        post(
                                "/api/payments/"
                                        + paymentId
                                        + "/cancel"
                        )
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "reason": "   "
                                        }
                                        """
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                );
    }

    @Test
    void malformedPaymentIdUsesErrorContract()
            throws Exception {

        mvc.perform(
                        get(
                                "/api/payments/not-a-uuid"
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
                        post("/api/payments")
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
    void paymentEndpointsKeepSecurityAndCsrfProtection()
            throws Exception {

        var sale = createSale(
                1,
                "8500"
        );

        mvc.perform(
                        post("/api/payments")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        paymentJson(
                                                sale.id(),
                                                "CASH"
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
                                "/api/payments/"
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

    private String createPayment(
            UUID saleId,
            String method
    ) throws Exception {

        String body = mvc.perform(
                        post("/api/payments")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        paymentJson(
                                                saleId,
                                                method
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return mapper.readTree(body)
                .get("id")
                .asText();
    }

    private String paymentJson(
            UUID saleId,
            String method
    ) {
        return """
                {
                  "saleId": "%s",
                  "method": "%s",
                  "reference": "TEST-REFERENCE",
                  "comment": "Тестовая оплата"
                }
                """.formatted(
                saleId,
                method
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
                    inventory_transfer_cost_origins, inventory_transfers, inventory_cost_movements, inventory_cost_allocations, inventory_cost_restorations, inventory_cost_write_offs, inventory_cost_layers, exchange_settlements, exchanges, refunds,
                    return_items, returns, payments,
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
                    'PAYMENT',
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