package kg.chairx.refund;

import tools.jackson.databind.json.JsonMapper;
import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.refund.api.CreateRefundRequest;
import kg.chairx.refund.domain.RefundMethod;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static kg.chairx.inventory.domain.StockMovementType.ADJUSTMENT_IN;
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
@WithMockUser(username = "refund-api-tester")
class RefundApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SaleService sales;

    @Autowired
    PaymentService payments;

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
                values (
                    ?,
                    'Refund API fixture',
                    true,
                    now(),
                    now()
                )
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
                values (
                    ?,
                    ?,
                    'Black',
                    8500,
                    true,
                    now(),
                    now()
                )
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
                    'Refund API Customer',
                    '+996555000002',
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
                        "REFUND_API_FIXTURE",
                        UUID.randomUUID(),
                        "refund-api"
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
    void createRefundReturns201AndLocation()
            throws Exception {

        var sale = createPaidSale();

        UUID key = UUID.randomUUID();

        CreateRefundRequest request =
                new CreateRefundRequest(
                        sale.id(),
                        null,
                        new BigDecimal("2500"),
                        RefundMethod.TRANSFER,
                        "Частичный возврат",
                        "MBANK-REFUND-1",
                        "Возврат клиенту",
                        key
                );

        mvc.perform(
                        post("/api/refunds")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        mapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        header().string(
                                "Location",
                                org.hamcrest.Matchers.matchesPattern(
                                        "/api/refunds/[0-9a-fA-F-]{36}"
                                )
                        )
                )
                .andExpect(
                        jsonPath("$.id").exists()
                )
                .andExpect(
                        jsonPath("$.saleId")
                                .value(
                                        sale.id().toString()
                                )
                )
                .andExpect(
                        jsonPath("$.returnId")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.amount")
                                .value(2500)
                )
                .andExpect(
                        jsonPath("$.method")
                                .value("TRANSFER")
                )
                .andExpect(
                        jsonPath("$.reason")
                                .value(
                                        "Частичный возврат"
                                )
                )
                .andExpect(
                        jsonPath("$.reference")
                                .value(
                                        "MBANK-REFUND-1"
                                )
                )
                .andExpect(
                        jsonPath("$.comment")
                                .value(
                                        "Возврат клиенту"
                                )
                )
                .andExpect(
                        jsonPath("$.refundedBy")
                                .value(
                                        "refund-api-tester"
                                )
                )
                .andExpect(
                        jsonPath("$.refundedAt")
                                .exists()
                );
    }

    @Test
    void createdRefundCanBeRetrieved()
            throws Exception {

        var sale = createPaidSale();

        String body = mvc.perform(
                        post("/api/refunds")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        mapper.writeValueAsString(
                                                request(
                                                        sale.id(),
                                                        "1500"
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isCreated()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID refundId =
                UUID.fromString(
                        mapper.readTree(body)
                                .get("id")
                                .asText()
                );

        mvc.perform(
                        get(
                                "/api/refunds/{id}",
                                refundId
                        )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        refundId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.saleId")
                                .value(
                                        sale.id().toString()
                                )
                )
                .andExpect(
                        jsonPath("$.amount")
                                .value(1500)
                );
    }

    @Test
    void refundsCanBeRetrievedBySale()
            throws Exception {

        var sale = createPaidSale();

        createRefundViaApi(
                sale.id(),
                "1000"
        );

        createRefundViaApi(
                sale.id(),
                "2000"
        );

        mvc.perform(
                        get(
                                "/api/refunds/sale/{saleId}",
                                sale.id()
                        )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[0].saleId")
                                .value(
                                        sale.id().toString()
                                )
                )
                .andExpect(
                        jsonPath("$[1].saleId")
                                .value(
                                        sale.id().toString()
                                )
                );
    }

    @Test
    void refundWithoutActivePaymentReturns409()
            throws Exception {

        var sale = createSale();

        mvc.perform(
                        post("/api/refunds")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        mapper.writeValueAsString(
                                                request(
                                                        sale.id(),
                                                        "1000"
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("SALE_NOT_PAID")
                );
    }

    @Test
    void excessiveRefundReturns409()
            throws Exception {

        var sale = createPaidSale();

        createRefundViaApi(
                sale.id(),
                "7000"
        );

        mvc.perform(
                        post("/api/refunds")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        mapper.writeValueAsString(
                                                request(
                                                        sale.id(),
                                                        "2000"
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "REFUND_AMOUNT_EXCEEDED"
                                )
                );
    }

    @Test
    void reusedIdempotencyKeyWithDifferentPayloadReturns409()
            throws Exception {

        var sale = createPaidSale();

        UUID key = UUID.randomUUID();

        CreateRefundRequest first =
                new CreateRefundRequest(
                        sale.id(),
                        null,
                        new BigDecimal("1000"),
                        RefundMethod.CASH,
                        "Компенсация",
                        null,
                        null,
                        key
                );

        CreateRefundRequest second =
                new CreateRefundRequest(
                        sale.id(),
                        null,
                        new BigDecimal("2000"),
                        RefundMethod.CASH,
                        "Компенсация",
                        null,
                        null,
                        key
                );

        mvc.perform(
                        post("/api/refunds")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        mapper.writeValueAsString(
                                                first
                                        )
                                )
                )
                .andExpect(
                        status().isCreated()
                );

        mvc.perform(
                        post("/api/refunds")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        mapper.writeValueAsString(
                                                second
                                        )
                                )
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "IDEMPOTENCY_KEY_REUSED"
                                )
                );
    }

    @Test
    void missingRefundReturns404()
            throws Exception {

        UUID missing = UUID.randomUUID();

        mvc.perform(
                        get(
                                "/api/refunds/{id}",
                                missing
                        )
                )
                .andExpect(
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "REFUND_NOT_FOUND"
                                )
                );
    }

    @Test
    void missingRequiredFieldsReturnsValidationError()
            throws Exception {

        mvc.perform(
                        post("/api/refunds")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "VALIDATION_ERROR"
                                )
                );
    }

    @Test
    void fractionalAmountReturnsValidationError()
            throws Exception {

        var sale = createPaidSale();

        CreateRefundRequest request =
                new CreateRefundRequest(
                        sale.id(),
                        null,
                        new BigDecimal("1000.50"),
                        RefundMethod.CASH,
                        "Возврат",
                        null,
                        null,
                        UUID.randomUUID()
                );

        mvc.perform(
                        post("/api/refunds")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        mapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "VALIDATION_ERROR"
                                )
                );
    }

    @Test
    void zeroAmountReturnsValidationError()
            throws Exception {

        var sale = createPaidSale();

        CreateRefundRequest request =
                new CreateRefundRequest(
                        sale.id(),
                        null,
                        BigDecimal.ZERO,
                        RefundMethod.CASH,
                        "Возврат",
                        null,
                        null,
                        UUID.randomUUID()
                );

        mvc.perform(
                        post("/api/refunds")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        mapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "VALIDATION_ERROR"
                                )
                );
    }

    @Test
    void malformedRefundIdReturns400()
            throws Exception {

        mvc.perform(
                        get(
                                "/api/refunds/not-a-uuid"
                        )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "INVALID_REQUEST"
                                )
                );
    }

    @Test
    void malformedJsonReturns400()
            throws Exception {

        mvc.perform(
                        post("/api/refunds")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                            "saleId":
                                        """
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "INVALID_REQUEST"
                                )
                );
    }

    @Test
    void postWithoutCsrfReturns403()
            throws Exception {

        var sale = createPaidSale();

        mvc.perform(
                        post("/api/refunds")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        mapper.writeValueAsString(
                                                request(
                                                        sale.id(),
                                                        "1000"
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isForbidden()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "ACCESS_DENIED"
                                )
                );
    }

    @Test
    @WithMockUser(username = "refund-api-tester")
    void sameIdempotentRequestReturnsSameRefund()
            throws Exception {

        var sale = createPaidSale();

        UUID key = UUID.randomUUID();

        CreateRefundRequest request =
                new CreateRefundRequest(
                        sale.id(),
                        null,
                        new BigDecimal("1000"),
                        RefundMethod.CASH,
                        "Повторяемый запрос",
                        null,
                        null,
                        key
                );

        String firstBody =
                mvc.perform(
                                post("/api/refunds")
                                        .with(csrf())
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                mapper.writeValueAsString(
                                                        request
                                                )
                                        )
                        )
                        .andExpect(
                                status().isCreated()
                        )
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String secondBody =
                mvc.perform(
                                post("/api/refunds")
                                        .with(csrf())
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                mapper.writeValueAsString(
                                                        request
                                                )
                                        )
                        )
                        .andExpect(
                                status().isCreated()
                        )
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String firstId =
                mapper.readTree(firstBody)
                        .get("id")
                        .asText();

        String secondId =
                mapper.readTree(secondBody)
                        .get("id")
                        .asText();

        org.assertj.core.api.Assertions
                .assertThat(secondId)
                .isEqualTo(firstId);
    }

    @Test
    @WithMockUser(
            username = "refund-api-tester"
    )
    void emptySaleHistoryReturnsEmptyArray()
            throws Exception {

        var sale = createPaidSale();

        mvc.perform(
                        get(
                                "/api/refunds/sale/{saleId}",
                                sale.id()
                        )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.length()")
                                .value(0)
                );
    }

    private void createRefundViaApi(
            UUID saleId,
            String amount
    ) throws Exception {

        mvc.perform(
                        post("/api/refunds")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        mapper.writeValueAsString(
                                                request(
                                                        saleId,
                                                        amount
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isCreated()
                );
    }

    private CreateRefundRequest request(
            UUID saleId,
            String amount
    ) {
        return new CreateRefundRequest(
                saleId,
                null,
                new BigDecimal(amount),
                RefundMethod.TRANSFER,
                "Возврат клиенту",
                null,
                null,
                UUID.randomUUID()
        );
    }

    private kg.chairx.sale.api.SaleResponse createPaidSale() {
        var sale = createSale();

        payments.create(
                new CreatePaymentRequest(
                        sale.id(),
                        PaymentMethod.TRANSFER,
                        null,
                        null
                )
        );

        return sale;
    }

    private kg.chairx.sale.api.SaleResponse createSale() {
        return sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customer,
                        FulfillmentType.SELF_PICKUP,
                        List.of(
                                new CreateSaleItemRequest(
                                        variant,
                                        home,
                                        1,
                                        new BigDecimal("8500")
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
                    'REFUND',
                    'RETURN',
                    'PAYMENT',
                    'DELIVERY',
                    'SALE',
                    'STOCK_MOVEMENT'
                )
                """);
    }

    private void assertTestDatabase() {
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject(
                        "select current_database()",
                        String.class
                )
        ).isEqualTo("chairx_test");
    }
}