package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #32: real PostgreSQL 17, HTTP Basic+CSRF and actual Spring services.
 *
 * Two different business days are CLOSED with legitimate REST requests.
 * A later dated expense updates both expectations without rewriting original
 * snapshots or creating fictitious adjustments to cash/bank. A controllable
 * clock exercises temporary RBAC grants and optimistic report versions.
 *
 * Runs ONLY with PostgreSQL Testcontainers (chairx_test); nothing is mocked
 * except time. Explicit teardown preserves the surrounding test suite.
 */
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, DailyClosingMultiDayAcceptanceTest.ClockConfig.class})
class DailyClosingMultiDayAcceptanceTest {
    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
    private static final LocalDate FIRST = LocalDate.of(2020, 10, 10);
    private static final LocalDate SECOND = FIRST.plusDays(1);
    private static final String PASSWORD = "test-password";

    private static final UUID ADMIN_ROLE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ROLE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EMPLOYEE_ROLE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>();
        void set(int dayOffset, int hour, int minute) {
            now.set(FIRST.plusDays(dayOffset).atTime(hour, minute).atZone(BISHKEK).toInstant());
        }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean @Primary MutableClock testClock() {
            MutableClock clock = new MutableClock();
            clock.set(0, 12, 0);
            return clock;
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    private record AccountSnapshot(BigDecimal balance, boolean initialized) { }
    private final Map<String, AccountSnapshot> originalAccounts = new HashMap<>();
    private final Set<UUID> originalMovements = new HashSet<>();
    private final List<UUID> users = new ArrayList<>();
    private final List<UUID> expenses = new ArrayList<>();

    private String employee;
    private String manager;
    private String admin;

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private BigDecimal balance(String account) {
        return jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code=?", BigDecimal.class, account);
    }

    private UUID reportId(LocalDate day) {
        return jdbc.queryForObject(
                "SELECT id FROM finance_daily_closings WHERE business_date=?",
                UUID.class, day);
    }

    private void assertBalances(String cash, String bank, long movementCount) {
        assertThat(balance("CASH")).isEqualByComparingTo(cash);
        assertThat(balance("BANK")).isEqualByComparingTo(bank);
        assertThat(count("SELECT count(*) FROM finance_movements")).isEqualTo(movementCount);
    }

    private void assertReportSnapshots(UUID first, UUID second) {
        assertThat(count("""
                SELECT count(*) FROM finance_daily_closing_sales_snapshots
                WHERE closing_id IN (?, ?)
                """, first, second)).isEqualTo(2);
        assertThat(count("""
                SELECT count(*) FROM finance_daily_closing_sales
                WHERE closing_id IN (?, ?)
                """, first, second)).isZero();
        assertThat(count("""
                SELECT count(*) FROM finance_daily_closing_accounts
                WHERE closing_id IN (?, ?)
                """, first, second)).isEqualTo(4);
    }

    private String user(UUID role) {
        UUID id = UUID.randomUUID();
        users.add(id);
        String username = "issue32_multi_" + id.toString().replace("-", "");
        jdbc.update("""
                INSERT INTO app_users(id,username,password_hash,display_name,active)
                VALUES(?,?,?,'Multi-day CI tester',TRUE)
                """, id, username, PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .encode(PASSWORD));
        jdbc.update("INSERT INTO security_user_roles(user_id,role_id) VALUES (?,?)", id, role);
        return username;
    }

    @BeforeEach
    void fixture() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("chairx_test");
        assertThat(count("""
                SELECT count(*) FROM finance_daily_closings
                WHERE business_date IN (?, ?)
                """, FIRST, SECOND)).isZero();
        originalAccounts.clear();
        originalMovements.clear();
        users.clear();
        expenses.clear();
        clock.set(0, 12, 0);
        originalMovements.addAll(jdbc.query("SELECT id FROM finance_movements",
                (rs, row) -> rs.getObject(1, UUID.class)));
        for (String account : List.of("CASH", "BANK")) {
            originalAccounts.put(account, jdbc.queryForObject("""
                    SELECT balance, opening_balance_initialized FROM finance_accounts
                    WHERE code=?
                    """, (rs, row) -> new AccountSnapshot(
                    rs.getBigDecimal(1), rs.getBoolean(2)), account));
            // A genuine dated OPENING_BALANCE journal fixture. Distinct
            // financial source IDs avoid colliding with application postings.
            BigDecimal previousLedger = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(amount),0) FROM finance_movements
                    WHERE account_code=?
                    """, BigDecimal.class, account);
            BigDecimal target = previousLedger.max(BigDecimal.ZERO).add(
                    account.equals("CASH") ? new BigDecimal("25000") : new BigDecimal("30000"));
            jdbc.update("""
                    INSERT INTO finance_movements
                      (id,account_code,amount,movement_type,source_type,source_id,
                       created_by,business_date,business_date_source)
                    VALUES (?, ?, ?, 'OPENING_BALANCE','OPENING_BALANCE',?,
                            'issue32-fixture',?,'POSTING_DATE')
                    """, UUID.randomUUID(), account, target.subtract(previousLedger),
                    UUID.randomUUID(), FIRST);
            jdbc.update("""
                    UPDATE finance_accounts SET balance=?,
                        opening_balance_initialized=TRUE WHERE code=?
                    """, target, account);
        }
        assertThat(count("SELECT count(*) FROM finance_document_posting_issues")).isZero();
        employee = user(EMPLOYEE_ROLE);
        manager = user(MANAGER_ROLE);
        admin = user(ADMIN_ROLE);
    }

    private static String url(LocalDate day) {
        return "/api/finance/closings/" + day;
    }

    private ResultActions request(String username, MockHttpServletRequestBuilder call,
                                  String json) throws Exception {
        call.with(httpBasic(username, PASSWORD)).with(csrf());
        if (json != null) call.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(call);
    }

    private ResultActions open(String username, LocalDate day) throws Exception {
        return request(username, post(url(day) + "/unlock"),
                "{\"reason\":\"Reconcile dated report with branch manager\"}");
    }

    private ResultActions closeDay(String username, LocalDate day,
                                   String actualCash, String cashNote,
                                   String actualBank) throws Exception {
        String body = """
                {"actualCash":%s,"cashNote":%s,"actualBank":%s}
                """.formatted(actualCash,
                cashNote == null ? "null" : "\"" + cashNote + "\"", actualBank);
        return request(username, post(url(day)), body);
    }

    private ResultActions edit(String username, LocalDate day, long expectedVersion,
                               String actualCash, String note) throws Exception {
        return request(username, put(url(day)), """
                {"expectedVersion":%d,"actualCash":%s,
                 "cashNote":"%s","actualBank":30000,
                 "reason":"Verified physical cash recount"}
                """.formatted(expectedVersion, actualCash, note));
    }

    private String expenseBody(UUID key, String amount) {
        return """
                {"idempotencyKey":"%s","category":"DELIVERY","amount":%s,
                 "paymentMethod":"CASH","expenseDate":"%s",
                 "comment":"Forgotten delivery from first closed day"}
                """.formatted(key, amount, FIRST);
    }

    @Test
    void crossDayCorrectionKeepsOriginalsSnapshotsAndEnforcesScopedTimeGrants() throws Exception {
        long movementsAtSetup = count("SELECT count(*) FROM finance_movements");
        BigDecimal initialCash = balance("CASH");
        BigDecimal initialBank = balance("BANK");
        // Test fixture is fresh with the expected 25,000 / 30,000 som.
        assertThat(initialCash).isEqualByComparingTo("25000");
        assertThat(initialBank).isEqualByComparingTo("30000");

        clock.set(0, 12, 0);
        closeDay(employee, FIRST, "24000", "Observed shortage before investigation",
                "30000").andExpect(status().isCreated())
                .andExpect(jsonPath("$.cash.expected").value(25000))
                .andExpect(jsonPath("$.cash.actual").value(24000))
                .andExpect(jsonPath("$.version").value(0));
        clock.set(1, 12, 0);
        closeDay(employee, SECOND, "25000", null,
                "30000").andExpect(status().isCreated())
                .andExpect(jsonPath("$.cash.expected").value(25000))
                .andExpect(jsonPath("$.version").value(0));
        UUID firstId = reportId(FIRST);
        UUID secondId = reportId(SECOND);
        assertBalances("25000", "30000", movementsAtSetup);
        assertReportSnapshots(firstId, secondId);

        // Three days after first closing: ordinary employee cannot edit.
        clock.set(3, 15, 0);
        UUID expenseKey = UUID.randomUUID();
        request(employee, post(url(FIRST) + "/expenses"),
                expenseBody(expenseKey, "1000"))
                .andExpect(status().isForbidden());
        assertThat(count("SELECT count(*) FROM expenses WHERE idempotency_key=?",
                expenseKey)).isZero();
        assertBalances("25000", "30000", movementsAtSetup);

        // Even this user's permission is date-scoped, not a finance-wide pass.
        open(employee, FIRST).andExpect(status().isForbidden());
        open(manager, FIRST).andExpect(status().isOk());
        request(employee, post(url(SECOND) + "/expenses"), expenseBody(expenseKey, "1000"))
                .andExpect(status().isBadRequest()); // wrong expense/closing date
        assertThat(count("SELECT count(*) FROM expenses WHERE idempotency_key=?",
                expenseKey)).isZero();

        request(employee, post(url(FIRST) + "/expenses"),
                expenseBody(expenseKey, "1000"))
                .andExpect(status().isCreated());
        UUID expenseId = jdbc.queryForObject(
                "SELECT id FROM expenses WHERE idempotency_key=?", UUID.class, expenseKey);
        expenses.add(expenseId);
        assertBalances("24000", "30000", movementsAtSetup + 1);
        assertThat(count("""
                SELECT count(*) FROM finance_movements
                WHERE source_type='EXPENSE' AND source_id=?
                    AND business_date=? AND business_date_source='HISTORICAL_CORRECTION'
                """, expenseId, FIRST)).isEqualTo(1);
        assertReportSnapshots(firstId, secondId);

        // One atomic posting revised BOTH closed reports while preserving
        // initial expected rows and unchanged immutable sales snapshot rows.
        for (UUID id : List.of(firstId, secondId)) {
            assertThat(jdbc.queryForObject("""
                    SELECT expected_balance FROM finance_daily_closing_accounts
                    WHERE closing_id=? AND account_code='CASH'
                    """, BigDecimal.class, id)).isEqualByComparingTo("25000");
            assertThat(jdbc.queryForObject("""
                    SELECT expected_balance FROM finance_daily_closing_adjustments
                    WHERE closing_id=? AND account_code='CASH'
                    """, BigDecimal.class, id)).isEqualByComparingTo("24000");
            assertThat(count("""
                    SELECT version FROM finance_daily_closings WHERE id=?
                    """, id)).isEqualTo(1);
        }
        request(manager, get(url(FIRST)), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.cash.expected").value(24000))
                .andExpect(jsonPath("$.cash.originalExpected").value(25000))
                .andExpect(jsonPath("$.cash.actual").value(24000))
                .andExpect(jsonPath("$.cash.difference").value(0))
                .andExpect(jsonPath("$.cash.recalculated").value(true))
                .andExpect(jsonPath("$.cash.requiresReview").value(false));
        request(manager, get(url(SECOND)), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.cash.expected").value(24000))
                .andExpect(jsonPath("$.cash.originalExpected").value(25000))
                .andExpect(jsonPath("$.cash.actual").value(25000))
                .andExpect(jsonPath("$.cash.requiresReview").value(true))
                .andExpect(jsonPath("$.bank.recalculated").value(false));
        request(manager, get(url(FIRST) + "/sales"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotAvailable").value(true))
                .andExpect(jsonPath("$.totals.orders").value(0));
        request(manager, get(url(SECOND) + "/sales"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotAvailable").value(true))
                .andExpect(jsonPath("$.totals.orders").value(0));

        // Same payload + exact key is safe: no duplicate expense, audit version
        // or movement; reusing the key with another amount conflicts.
        request(employee, post(url(FIRST) + "/expenses"),
                expenseBody(expenseKey, "1000"))
                .andExpect(status().isCreated());
        request(employee, post(url(FIRST) + "/expenses"),
                expenseBody(expenseKey, "2000"))
                .andExpect(status().isConflict());
        assertThat(count("SELECT count(*) FROM expenses WHERE idempotency_key=?",
                expenseKey)).isEqualTo(1);
        assertBalances("24000", "30000", movementsAtSetup + 1);
        assertThat(count("""
                SELECT version FROM finance_daily_closings WHERE id=?
                """, secondId)).isEqualTo(1);

        // Original report did not silently rewrite its sales snapshot or
        // physical cash count; later report is REVIEW_REQUIRED until human
        // explicitly recounts it. First-date unlock does NOT unlock second.
        edit(employee, SECOND, 1, "24000", "Recount second day")
                .andExpect(status().isForbidden());
        open(manager, SECOND).andExpect(status().isOk());
        edit(employee, SECOND, 0, "24000", "Stale optimistic version")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_VERSION_CONFLICT"));
        assertThat(count("SELECT version FROM finance_daily_closings WHERE id=?",
                secondId)).isEqualTo(1);
        edit(employee, SECOND, 1, "24000", "Recount second day")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.cash.requiresReview").value(false));
        assertBalances("24000", "30000", movementsAtSetup + 1);
        assertReportSnapshots(firstId, secondId);

        // Audit retains previous observation and every revision even though
        // the current actual count may be updated by a new human recount.
        assertThat(count("""
                SELECT count(*) FROM audit_entries
                WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?
                  AND action='REPORT_CLOSED'
                """, secondId)).isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM audit_entries
                WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?
                  AND action='REPORT_EXPECTED_RECALCULATED'
                """, secondId)).isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM audit_entries
                WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?
                  AND action='REPORT_UPDATED' AND before_state::text LIKE '%25000%'
                """, secondId)).isEqualTo(1);
        request(manager, get(url(SECOND) + "/history"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").exists());

        // ADMIN explicitly revokes FIRST grant. Future expense rejected
        // before inserting a document or altering balance/revision.
        request(admin, post(url(FIRST) + "/lock"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(true));
        request(employee, get(url(FIRST) + "/access"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false));
        UUID refused = UUID.randomUUID();
        request(employee, post(url(FIRST) + "/expenses"), expenseBody(refused, "50"))
                .andExpect(status().isForbidden());
        assertThat(count("SELECT count(*) FROM expenses WHERE idempotency_key=?",
                refused)).isZero();
        assertThat(count("SELECT version FROM finance_daily_closings WHERE id=?",
                firstId)).isEqualTo(1);
        assertBalances("24000", "30000", movementsAtSetup + 1);

        // SECOND grant also expires after 1 hour, without cron/manual DB
        // intervention. Its already committed revision remains intact.
        clock.set(3, 16, 0);
        request(employee, get(url(SECOND) + "/access"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false));
        edit(employee, SECOND, 2, "24000", "Expired grant")
                .andExpect(status().isForbidden());
        assertThat(count("SELECT version FROM finance_daily_closings WHERE id=?",
                secondId)).isEqualTo(2);
        assertBalances("24000", "30000", movementsAtSetup + 1);

        // Admin can re-open historical records even when the standard
        // manager window is gone. Revocation leaves a durable audit trail.
        clock.set(7, 15, 0);
        open(manager, FIRST).andExpect(status().isForbidden());
        open(admin, FIRST).andExpect(status().isOk());
        request(admin, post(url(FIRST) + "/lock"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(true));
        request(manager, get(url(FIRST) + "/history"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='REPORT_RELOCKED')]").isNotEmpty());
        assertBalances("24000", "30000", movementsAtSetup + 1);
        assertReportSnapshots(firstId, secondId);
    }

    @AfterEach
    void cleanup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("chairx_test");
        for (LocalDate day : List.of(FIRST, SECOND)) {
            List<UUID> ids = jdbc.query(
                    "SELECT id FROM finance_daily_closings WHERE business_date=?",
                    (rs, row) -> rs.getObject(1, UUID.class), day);
            for (UUID id : ids) {
                jdbc.update("""
                        DELETE FROM audit_entries
                        WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?
                        """, id);
                jdbc.update("DELETE FROM finance_daily_closing_sales WHERE closing_id=?", id);
                jdbc.update("DELETE FROM finance_daily_closing_sales_snapshots WHERE closing_id=?", id);
                jdbc.update("DELETE FROM finance_daily_closing_adjustments WHERE closing_id=?", id);
                jdbc.update("DELETE FROM finance_daily_closing_accounts WHERE closing_id=?", id);
                jdbc.update("DELETE FROM finance_daily_closings WHERE id=?", id);
            }
            UUID provisional = UUID.nameUUIDFromBytes(
                    ("FINANCE_DAILY_CLOSING:" + day).getBytes(StandardCharsets.UTF_8));
            jdbc.update("""
                    DELETE FROM audit_entries
                    WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?
                    """, provisional);
            jdbc.update("DELETE FROM finance_daily_closing_unlocks WHERE business_date=?", day);
            jdbc.update("DELETE FROM finance_daily_closing_access_locks WHERE business_date=?", day);
        }
        for (UUID id : jdbc.query("SELECT id FROM finance_movements",
                (rs, row) -> rs.getObject(1, UUID.class))) {
            if (!originalMovements.contains(id)) {
                jdbc.update("DELETE FROM finance_movements WHERE id=?", id);
            }
        }
        for (UUID id : expenses) {
            jdbc.update("DELETE FROM expenses WHERE id=?", id);
        }
        for (UUID id : users) {
            jdbc.update("DELETE FROM security_user_roles WHERE user_id=?", id);
            jdbc.update("DELETE FROM app_users WHERE id=?", id);
        }
        for (var item : originalAccounts.entrySet()) {
            jdbc.update("""
                    UPDATE finance_accounts SET balance=?,opening_balance_initialized=?
                    WHERE code=?
                    """, item.getValue().balance(), item.getValue().initialized(), item.getKey());
        }
    }
}
