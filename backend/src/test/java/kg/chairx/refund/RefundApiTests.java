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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
@WithMockUser(username = "refund-api-tester", authorities = {"REFUNDS_READ","REFUNDS_CREATE"})
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
    void cashRefundPostsTodayWithoutChangingAlreadyClosedDay() throws Exception {
        var sale = createPaidSale();
        var yesterday = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Bishkek")).minusDays(1);
        UUID closingId = UUID.randomUUID();
        BigDecimal cashBefore = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
        jdbc.update("INSERT INTO finance_daily_closings(id,business_date,created_by) VALUES (?,?,'refund-test')",
                closingId, java.sql.Date.valueOf(yesterday));
        jdbc.update("""
                INSERT INTO finance_daily_closing_accounts(closing_id,account_code,expected_balance,actual_balance)
                SELECT ?,code,balance,balance FROM finance_accounts
                """, closingId);
        try {
            var request = new CreateRefundRequest(
                    sale.id(), null, new BigDecimal("2500"), RefundMethod.CASH,
                    "Денежный возврат после закрытия прошлого дня", null, null, UUID.randomUUID());
            String payload = mapper.writeValueAsString(request);
            String createdBody = mvc.perform(post("/api/refunds").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            UUID refundId = UUID.fromString(mapper.readTree(createdBody).get("id").asText());
            BigDecimal cashAfter = jdbc.queryForObject(
                    "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
            org.assertj.core.api.Assertions.assertThat(cashAfter)
                    .isEqualByComparingTo(cashBefore.subtract(new BigDecimal("2500")));
            BigDecimal historicalActual = jdbc.queryForObject("""
                    SELECT actual_balance FROM finance_daily_closing_accounts
                    WHERE closing_id=? AND account_code='CASH'
                    """, BigDecimal.class, closingId);
            org.assertj.core.api.Assertions.assertThat(historicalActual)
                    .isEqualByComparingTo(cashBefore);
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM finance_movements
                    WHERE source_type='REFUND' AND source_id=? AND account_code='CASH' AND amount=-2500
                    """, Integer.class, refundId)).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM finance_movements
                    WHERE source_type='REFUND' AND source_id=?
                      AND business_date=?
                    """, Integer.class, refundId,
                    java.sql.Date.valueOf(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Bishkek")))))
                    .isEqualTo(1);

            // A lost HTTP response can lead to a legitimate retry of the exact key.
            // The closed historical snapshot, refund document and CASH must not change.
            String replayBody = mvc.perform(post("/api/refunds").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(
                    mapper.readTree(replayBody).get("id").asText()).isEqualTo(refundId.toString());
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM refunds WHERE id=?", Integer.class, refundId)).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM finance_movements
                    WHERE source_type='REFUND' AND source_id=?
                    """, Integer.class, refundId)).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                    "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class))
                    .isEqualByComparingTo(cashAfter);
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                    SELECT actual_balance FROM finance_daily_closing_accounts
                    WHERE closing_id=? AND account_code='CASH'
                    """, BigDecimal.class, closingId)).isEqualByComparingTo(cashBefore);
        } finally {
            jdbc.update("DELETE FROM finance_daily_closing_accounts WHERE closing_id=?",closingId);
            jdbc.update("DELETE FROM finance_daily_closings WHERE id=?",closingId);
        }
    }

    @Test
    void realAuthorizedPriorDayClosingThenCurrentRefundPreservesOriginalSnapshot() throws Exception {
        // Unlike a hand-inserted closing row, this goes through the actual
        // HTTP security boundary, closing policy, snapshot and audit logic.
        var sale = createPaidSale();
        var yesterday = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Bishkek")).minusDays(1);
        var today = yesterday.plusDays(1);
        var paymentId = jdbc.queryForObject(
                "SELECT id FROM payments WHERE sale_id=? AND status='PAID'", UUID.class, sale.id());
        int dated = jdbc.update("""
                UPDATE finance_movements SET business_date=?,
                   created_at=(?::date + TIME '12:00') AT TIME ZONE 'Asia/Bishkek'
                WHERE source_type='PAYMENT' AND source_id=?
                """, yesterday, yesterday, paymentId);
        org.assertj.core.api.Assertions.assertThat(dated).isEqualTo(1);
        var originalPaymentJournal = jdbc.queryForMap("""
                SELECT id, business_date, account_code, amount, created_at
                FROM finance_movements WHERE source_type='PAYMENT' AND source_id=?
                """,paymentId);
        var authorized = user("finance-closing-operator").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_CLOSE"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"));
        UUID closeId=null;
        try {
            String previewJson=mvc.perform(get("/api/finance/closings/"+yesterday)
                        .with(authorized))
                    .andReturn().getResponse().getContentAsString();
            // A missing yesterday closing is expected; obtain the authoritative
            // balance preview instead of inventing the physical observations.
            org.assertj.core.api.Assertions.assertThat(previewJson).isNotNull();
            String preview=mvc.perform(get("/api/finance/closings/"+yesterday+"/preview")
                    .with(user("finance-closing-operator").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            var expected=mapper.readTree(preview);
            String closeBody=mapper.writeValueAsString(java.util.Map.of(
                    "actualCash",expected.get("expectedCash").decimalValue(),
                    "actualBank",expected.get("expectedBank").decimalValue()));
            String created=mvc.perform(post("/api/finance/closings/"+yesterday)
                    .with(csrf()).with(user("finance-closing-operator").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_CLOSE")))
                    .contentType(MediaType.APPLICATION_JSON).content(closeBody))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            closeId=UUID.fromString(mapper.readTree(created).get("id").asText());
            var frozen=jdbc.queryForList("""
                    SELECT account_code,expected_balance,actual_balance
                    FROM finance_daily_closing_accounts WHERE closing_id=?
                    ORDER BY account_code
                    """,closeId);
            var frozenHeader=jdbc.queryForMap("""
                    SELECT id,business_date,created_by,version,created_at
                    FROM finance_daily_closings WHERE id=?
                    """,closeId);
            BigDecimal bankBefore=jdbc.queryForObject(
                    "SELECT balance FROM finance_accounts WHERE code='BANK'",BigDecimal.class);
            var request=new CreateRefundRequest(sale.id(),null,new BigDecimal("2500"),
                    RefundMethod.TRANSFER,"Возврат после подтверждённого закрытия",null,null,
                    UUID.randomUUID());
            String payload=mapper.writeValueAsString(request);
            String first=mvc.perform(post("/api/refunds").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            UUID refundId=UUID.fromString(mapper.readTree(first).get("id").asText());
            String replay=mvc.perform(post("/api/refunds").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(mapper.readTree(replay).get("id").asText())
                    .isEqualTo(refundId.toString());
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                    "SELECT balance FROM finance_accounts WHERE code='BANK'",BigDecimal.class))
                    .isEqualByComparingTo(bankBefore.subtract(new BigDecimal("2500")));
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM finance_movements
                    WHERE source_type='REFUND' AND source_id=?
                      AND business_date=? AND account_code='BANK' AND amount=-2500
                    """,Long.class,refundId,today)).isEqualTo(1L);
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForMap("""
                    SELECT id,business_date,account_code,amount,created_at
                    FROM finance_movements WHERE source_type='PAYMENT' AND source_id=?
                    """,paymentId)).isEqualTo(originalPaymentJournal);
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForList("""
                    SELECT account_code,expected_balance,actual_balance
                    FROM finance_daily_closing_accounts WHERE closing_id=?
                    ORDER BY account_code
                    """,closeId)).isEqualTo(frozen);
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForMap("""
                    SELECT id,business_date,created_by,version,created_at
                    FROM finance_daily_closings WHERE id=?
                    """,closeId)).isEqualTo(frozenHeader);
        } finally {
            if(closeId!=null) {
                jdbc.update("DELETE FROM audit_entries WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?",closeId);
                jdbc.update("DELETE FROM finance_daily_closing_sales WHERE closing_id=?",closeId);
                jdbc.update("DELETE FROM finance_daily_closing_sales_snapshots WHERE closing_id=?",closeId);
                jdbc.update("DELETE FROM finance_daily_closing_adjustments WHERE closing_id=?",closeId);
                jdbc.update("DELETE FROM finance_daily_closing_accounts WHERE closing_id=?",closeId);
                jdbc.update("DELETE FROM finance_daily_closings WHERE id=?",closeId);
            }
            jdbc.update("DELETE FROM finance_daily_closing_access_locks WHERE business_date=?",yesterday);
        }
    }

    @Test
    void unauthorizedOrCsrfMissingRefundCannotChangeBankOrDocuments() throws Exception {
        var sale = createPaidSale();
        BigDecimal bankBefore = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class);
        long movementCount = jdbc.queryForObject(
                "SELECT count(*) FROM finance_movements WHERE source_type='REFUND'",
                Long.class);
        long refundCount = jdbc.queryForObject("SELECT count(*) FROM refunds", Long.class);
        var input = new CreateRefundRequest(
                sale.id(), null, new BigDecimal("2500"), RefundMethod.TRANSFER,
                "Проверка отказа в доступе", null, null, UUID.randomUUID());
        String body = mapper.writeValueAsString(input);

        // An authenticated catalog-only employee cannot post a refund.
        mvc.perform(post("/api/refunds").with(csrf())
                .with(user("catalog-only").authorities(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority("PRODUCTS_READ")))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        // Even a refund-authorized employee requires CSRF for mutation.
        mvc.perform(post("/api/refunds")
                .with(user("refund-operator").authorities(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority("REFUNDS_CREATE")))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class))
                .isEqualByComparingTo(bankBefore);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refunds", Long.class)).isEqualTo(refundCount);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_movements WHERE source_type='REFUND'",
                Long.class)).isEqualTo(movementCount);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT status FROM sales WHERE id=?", String.class, sale.id()))
                .isEqualTo("CONFIRMED");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_entries WHERE entity_type='REFUND'",
                Long.class)).isZero();
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
    @WithMockUser(username = "refund-api-tester", authorities = {"REFUNDS_READ","REFUNDS_CREATE"})
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
    @WithMockUser(username = "refund-api-tester", authorities = {"REFUNDS_READ","REFUNDS_CREATE"})
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
                truncate table sale_comments,
                    inventory_transfer_cost_origins, inventory_transfers, inventory_cost_movements, inventory_cost_allocations, inventory_cost_restorations, inventory_cost_write_offs, inventory_cost_layers, refunds,
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