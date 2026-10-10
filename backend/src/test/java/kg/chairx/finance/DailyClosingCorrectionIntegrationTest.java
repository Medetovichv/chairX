package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.finance.domain.FinanceAccount;
import kg.chairx.finance.application.FinanceTransferService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P21-H: genuine PostgreSQL + HTTP Basic + CSRF tests for historical
 * bookkeeping and expense corrections. Checks balances, dated movements,
 * idempotency, snapshots, audit and rollback, not merely HTTP response codes.
 */
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class DailyClosingCorrectionIntegrationTest {
    private static final ZoneId BUSINESS = ZoneId.of("Asia/Bishkek");
    private static final UUID MANAGER_ROLE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EMPLOYEE_ROLE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired FinanceTransferService transfers;

    private record AccountState(BigDecimal balance, boolean initialized) {}
    private final Map<String, AccountState> originalAccounts = new HashMap<>();
    private final Set<UUID> originalMovements = new HashSet<>();
    private final List<UUID> newUsers = new ArrayList<>();
    private final List<UUID> newExpenses = new ArrayList<>();
    private final List<UUID> newTransfers = new ArrayList<>();
    private final List<LocalDate> closingDates = new ArrayList<>();

    private LocalDate reportDate;
    private BigDecimal originalCash;
    private BigDecimal originalBank;
    private String employee;
    private String manager;

    private BigDecimal ledger(String account) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM finance_movements
                WHERE account_code = ?
                """, BigDecimal.class, account);
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    @BeforeEach
    void setUp() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        reportDate = LocalDate.now(BUSINESS).minusDays(2);
        for (String account : List.of("CASH", "BANK")) {
            originalAccounts.put(account, jdbc.queryForObject("""
                    SELECT balance, opening_balance_initialized
                    FROM finance_accounts WHERE code = ?
                    """, (rs, row) -> new AccountState(rs.getBigDecimal(1), rs.getBoolean(2)), account));
        }
        originalMovements.addAll(jdbc.query("SELECT id FROM finance_movements",
                (rs, row) -> rs.getObject(1, UUID.class)));

        // Independent, journal-backed balances, no direct fictitious cash
        // corrections inside a normal business API. Fixtures have their own
        // OPENING_BALANCE entries and are fully removed in @AfterEach.
        originalCash = fund("CASH", amount("25000"));
        originalBank = fund("BANK", amount("30000"));
        employee = newUser(EMPLOYEE_ROLE);
        manager = newUser(MANAGER_ROLE);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_document_posting_issues", Long.class)).isZero();
    }

    private BigDecimal fund(String account, BigDecimal minExtra) {
        BigDecimal priorLedger = ledger(account);
        BigDecimal target = priorLedger.max(BigDecimal.ZERO).add(minExtra);
        BigDecimal difference = target.subtract(priorLedger);
        jdbc.update("""
                INSERT INTO finance_movements
                 (id, account_code, amount, movement_type, source_type, source_id,
                  created_by, business_date, business_date_source)
                VALUES (?, ?, ?, 'OPENING_BALANCE', 'OPENING_BALANCE', ?,
                        'p21-fixture', ?, 'POSTING_DATE')
                """, UUID.randomUUID(), account, difference, UUID.randomUUID(), reportDate);
        jdbc.update("""
                UPDATE finance_accounts SET balance = ?, opening_balance_initialized = TRUE
                WHERE code = ?
                """, target, account);
        assertThat(ledger(account)).isEqualByComparingTo(target);
        return target;
    }

    private String newUser(UUID role) {
        UUID id = UUID.randomUUID();
        newUsers.add(id);
        String username = "p21_corr_" + id;
        jdbc.update("""
                INSERT INTO app_users (id, username, password_hash, display_name, active)
                VALUES (?, ?, ?, 'P21 correction test', TRUE)
                """, id, username,
                PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("test-password"));
        jdbc.update("INSERT INTO security_user_roles (user_id,role_id) VALUES (?,?)", id, role);
        return username;
    }

    private String url() {
        return "/api/finance/closings/" + reportDate;
    }

    private void unlockAndClose(BigDecimal cashActual, String cashNote) throws Exception {
        mvc.perform(post(url() + "/unlock").with(httpBasic(manager, "test-password"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Fix historic cash report\"}"))
                .andExpect(status().isOk());
        String body = String.format(Locale.ROOT,
                "{\"actualCash\":%s,\"cashNote\":\"%s\",\"actualBank\":%s}",
                cashActual.toPlainString(), cashNote, originalBank.toPlainString());
        mvc.perform(post(url()).with(httpBasic(employee, "test-password")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        closingDates.add(reportDate);
    }

    private String expenseJson(UUID key, BigDecimal amount) {
        return String.format(Locale.ROOT, """
                {"idempotencyKey":"%s","category":"DELIVERY","amount":%s,
                 "paymentMethod":"CASH","expenseDate":"%s",
                 "comment":"Previously forgotten delivery"}
                """, key, amount.toPlainString(), reportDate);
    }

    private UUID correct(UUID key, BigDecimal amount) throws Exception {
        String json = expenseJson(key, amount);
        var response = mvc.perform(post(url() + "/expenses")
                        .with(httpBasic(employee, "test-password")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // Source of truth for the committed expense is the idempotency key;
        // no JSON mapper or fragile response-text parsing is needed.
        UUID id = jdbc.queryForObject(
                "SELECT id FROM expenses WHERE idempotency_key = ?", UUID.class, key);
        if (!newExpenses.contains(id)) newExpenses.add(id);
        assertThat(response).contains(id.toString());
        return id;
    }

    private BigDecimal closingValue(String account, String column) {
        String expr = switch (column) {
            case "expected_balance" -> "COALESCE(r.expected_balance,a.expected_balance)";
            case "actual_balance" -> "a.actual_balance";
            case "difference" -> "a.actual_balance - COALESCE(r.expected_balance,a.expected_balance)";
            default -> throw new IllegalArgumentException("Unsupported column");
        };
        return jdbc.queryForObject("""
                SELECT %s FROM finance_daily_closing_accounts a
                JOIN finance_daily_closings c ON c.id=a.closing_id
                LEFT JOIN finance_daily_closing_adjustments r
                  ON r.closing_id=a.closing_id AND r.account_code=a.account_code
                WHERE c.business_date=? AND a.account_code=?
                """.formatted(expr), BigDecimal.class, reportDate, account);
    }

    @Test
    void lateExpenseExplainsShortageAndKeepsTodaysTransferOutOfYesterday() throws Exception {
        UUID transfer = UUID.randomUUID();
        transfers.transfer(transfer, FinanceAccount.CASH, FinanceAccount.BANK, amount("5000"), "p21-fixture");
        newTransfers.add(transfer);
        assertThat(jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class))
                .isEqualByComparingTo(originalCash.subtract(amount("5000")));

        // A transfer physically made today cannot change yesterday's expected
        // CASH or BANK. This also exercises both sides of transfer businessDate.
        mvc.perform(get(url() + "/preview").with(httpBasic(manager, "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedCash").isNumber())
                .andExpect(jsonPath("$.expectedBank").isNumber());
        var predicted = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM finance_movements
                WHERE account_code='CASH' AND business_date <= ?
                """, BigDecimal.class, reportDate);
        assertThat(predicted).isEqualByComparingTo(originalCash);

        unlockAndClose(originalCash.subtract(amount("1000")), "Missing 1000; cause unknown");
        assertThat(closingValue("CASH", "expected_balance")).isEqualByComparingTo(originalCash);
        assertThat(closingValue("CASH", "difference")).isEqualByComparingTo("-1000");

        UUID key = UUID.randomUUID();
        UUID expense = correct(key, amount("1000"));
        assertThat(closingValue("CASH", "expected_balance"))
                .isEqualByComparingTo(originalCash.subtract(amount("1000")));
        assertThat(closingValue("CASH", "actual_balance"))
                .isEqualByComparingTo(originalCash.subtract(amount("1000")));
        assertThat(closingValue("CASH", "difference")).isEqualByComparingTo("0");
        assertThat(closingValue("BANK", "expected_balance")).isEqualByComparingTo(originalBank);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM finance_movements
                WHERE source_type='EXPENSE' AND source_id=? AND amount=-1000
                  AND business_date=? AND business_date_source='HISTORICAL_CORRECTION'
                  AND (created_at AT TIME ZONE 'Asia/Bishkek')::date >= business_date
                """, Integer.class, expense, reportDate)).isEqualTo(1);

        BigDecimal after = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
        assertThat(after).isEqualByComparingTo(originalCash.subtract(amount("6000")));

        UUID replay = correct(key, amount("1000"));
        assertThat(replay).isEqualTo(expense);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses WHERE idempotency_key=?",
                Integer.class, key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_movements WHERE source_id=?",
                Integer.class, expense)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'",
                BigDecimal.class)).isEqualByComparingTo(after);

        mvc.perform(post(url() + "/expenses").with(httpBasic(employee, "test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson(key, amount("2000"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPENSE_IDEMPOTENCY_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'",
                BigDecimal.class)).isEqualByComparingTo(after);

        UUID closingId = jdbc.queryForObject(
                "SELECT id FROM finance_daily_closings WHERE business_date=?", UUID.class, reportDate);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_entries
                WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?
                  AND action='REPORT_EXPENSE_CORRECTED'
                """, Long.class, closingId)).isEqualTo(1);
    }

    @Test
    void partialExpenseLeavesRealUnexplainedDifferenceAndNote() throws Exception {
        unlockAndClose(originalCash.subtract(amount("1000")), "Still researching cash difference");
        correct(UUID.randomUUID(), amount("600"));
        assertThat(closingValue("CASH", "expected_balance"))
                .isEqualByComparingTo(originalCash.subtract(amount("600")));
        assertThat(closingValue("CASH", "difference")).isEqualByComparingTo("-400");
        assertThat(jdbc.queryForObject("""
                SELECT a.note FROM finance_daily_closing_accounts a
                JOIN finance_daily_closings c ON c.id=a.closing_id
                WHERE c.business_date=? AND a.account_code='CASH'
                """, String.class, reportDate)).isEqualTo("Still researching cash difference");
    }

    @Test
    void insufficientCashRollsBackExpensePostingAndClosingRevision() throws Exception {
        unlockAndClose(originalCash, "");
        UUID key = UUID.randomUUID();
        BigDecimal before = jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'",
                BigDecimal.class);
        long version = jdbc.queryForObject("SELECT version FROM finance_daily_closings WHERE business_date=?",
                Long.class, reportDate);
        mvc.perform(post(url() + "/expenses").with(httpBasic(employee, "test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson(key, before.add(BigDecimal.ONE))))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses WHERE idempotency_key=?",
                Long.class, key)).isZero();
        assertThat(jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'",
                BigDecimal.class)).isEqualByComparingTo(before);
        assertThat(jdbc.queryForObject("SELECT version FROM finance_daily_closings WHERE business_date=?",
                Long.class, reportDate)).isEqualTo(version);
    }

    @Test
    void laterClosedDayHasAuditedRevisionButKeepsOriginalSnapshot() throws Exception {
        unlockAndClose(originalCash.subtract(amount("1000")), "Unexplained shortage");
        LocalDate later = reportDate.plusDays(1);
        UUID laterId = UUID.randomUUID();
        closingDates.add(later);
        jdbc.update("INSERT INTO finance_daily_closings(id,business_date,created_by) VALUES(?,?,?)",
                laterId, later, "p21-fixture");
        jdbc.update("""
                INSERT INTO finance_daily_closing_accounts
                 (closing_id, account_code, expected_balance, actual_balance)
                VALUES (?,'CASH',?,?), (?,'BANK',?,?)
                """, laterId, originalCash, originalCash, laterId, originalBank, originalBank);
        UUID expense = correct(UUID.randomUUID(), amount("1000"));
        assertThat(closingValue("CASH", "difference")).isEqualByComparingTo("0");

        assertThat(jdbc.queryForObject("""
                SELECT expected_balance FROM finance_daily_closing_accounts
                WHERE closing_id=? AND account_code='CASH'
                """, BigDecimal.class, laterId)).isEqualByComparingTo(originalCash);
        assertThat(jdbc.queryForObject("""
                SELECT expected_balance FROM finance_daily_closing_adjustments
                WHERE closing_id=? AND account_code='CASH'
                """, BigDecimal.class, laterId))
                .isEqualByComparingTo(originalCash.subtract(amount("1000")));
        assertThat(jdbc.queryForObject("SELECT version FROM finance_daily_closings WHERE id=?",
                Long.class, laterId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_entries
                WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?
                  AND action='REPORT_EXPECTED_RECALCULATED'
                """, Long.class, laterId)).isEqualTo(1);
        mvc.perform(get("/api/finance/closings/" + later)
                        .with(httpBasic(manager, "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cash.recalculated").value(true))
                .andExpect(jsonPath("$.cash.requiresReview").value(true))
                .andExpect(jsonPath("$.bank.recalculated").value(false));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM finance_movements
                WHERE source_type='EXPENSE' AND source_id=?
                """, Long.class, expense)).isEqualTo(1);
    }

    @Test
    void negativeHistoricalBalanceRollsBackOtherwiseAffordableExpense() throws Exception {
        // Money received today can finance today's expense but cannot justify
        // a retroactive debit that makes the preceding date negative.
        UUID transfer = UUID.randomUUID();
        transfers.transfer(transfer, FinanceAccount.BANK, FinanceAccount.CASH,
                amount("5000"), "p21-fixture");
        newTransfers.add(transfer);
        unlockAndClose(originalCash, "");
        UUID key = UUID.randomUUID();
        BigDecimal live = jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'",
                BigDecimal.class);
        BigDecimal expense = originalCash.add(amount("1000"));
        assertThat(live).isGreaterThanOrEqualTo(expense);

        mvc.perform(post(url() + "/expenses").with(httpBasic(employee, "test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson(key, expense)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNRECONCILED_FINANCIAL_BALANCE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses WHERE idempotency_key=?",
                Long.class, key)).isZero();
        assertThat(jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'",
                BigDecimal.class)).isEqualByComparingTo(live);
        assertThat(closingValue("CASH", "expected_balance")).isEqualByComparingTo(originalCash);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_movements WHERE source_type='EXPENSE'",
                Long.class)).isEqualTo(0);
    }

    @Test
    void concurrentRetryCreatesExactlyOneExpenseAndJournalMovement() throws Exception {
        unlockAndClose(originalCash.subtract(amount("1000")), "Cash shortage");
        UUID key = UUID.randomUUID();
        String json = expenseJson(key, amount("1000"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                attempts.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    return mvc.perform(post(url() + "/expenses")
                                    .with(httpBasic(employee, "test-password")).with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON).content(json))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Integer> response : attempts) {
                assertThat(response.get(30, TimeUnit.SECONDS)).isEqualTo(201);
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
        UUID expense = jdbc.queryForObject(
                "SELECT id FROM expenses WHERE idempotency_key=?", UUID.class, key);
        newExpenses.add(expense);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM expenses WHERE idempotency_key=?", Long.class, key)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_movements WHERE source_type='EXPENSE' AND source_id=?",
                Long.class, expense)).isEqualTo(1);
        assertThat(closingValue("CASH", "difference")).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject(
                "SELECT version FROM finance_daily_closings WHERE business_date=?",
                Long.class, reportDate)).isEqualTo(1);
    }

    @Test
    void concurrentReportUpdatesHaveOneWinnerAndNoLostChanges() throws Exception {
        unlockAndClose(originalCash, "");
        String bodyA = String.format(Locale.ROOT, """
                {"expectedVersion":0,"actualCash":%s,"cashNote":"Count A",
                 "actualBank":%s,"reason":"Recount A"}
                """, originalCash.subtract(amount("10")).toPlainString(), originalBank.toPlainString());
        String bodyB = String.format(Locale.ROOT, """
                {"expectedVersion":0,"actualCash":%s,"cashNote":"Count B",
                 "actualBank":%s,"reason":"Recount B"}
                """, originalCash.subtract(amount("20")).toPlainString(), originalBank.toPlainString());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String body : List.of(bodyA, bodyB)) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    return mvc.perform(put(url()).with(httpBasic(employee, "test-password"))
                                    .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Set<Integer> responses = new HashSet<>();
            for (Future<Integer> result : results) responses.add(result.get(30, TimeUnit.SECONDS));
            assertThat(responses).containsExactlyInAnyOrder(200, 409);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "SELECT version FROM finance_daily_closings WHERE business_date=?",
                Long.class, reportDate)).isEqualTo(1);
        assertThat(closingValue("CASH", "actual_balance")).isIn(
                originalCash.subtract(amount("10")), originalCash.subtract(amount("20")));
        assertThat(closingValue("CASH", "expected_balance")).isEqualByComparingTo(originalCash);
    }

    @AfterEach
    void tearDown() {
        // Testcontainers' dedicated test database must be verified before any
        // potentially destructive cleanup. Never touch an arbitrary DB.
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        for (LocalDate day : closingDates) {
            UUID id = jdbc.queryForObject(
                    "SELECT id FROM finance_daily_closings WHERE business_date=?", UUID.class, day);
            jdbc.update("DELETE FROM audit_entries WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?", id);
            jdbc.update("DELETE FROM finance_daily_closing_accounts WHERE closing_id=?", id);
            jdbc.update("DELETE FROM finance_daily_closings WHERE id=?", id);
        }
        Set<LocalDate> affectedDates = new HashSet<>(closingDates);
        if (reportDate != null) affectedDates.add(reportDate);
        for (LocalDate day : affectedDates) {
            UUID provisional = UUID.nameUUIDFromBytes(
                    ("FINANCE_DAILY_CLOSING:" + day).getBytes(StandardCharsets.UTF_8));
            jdbc.update("DELETE FROM audit_entries WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?",
                    provisional);
            jdbc.update("DELETE FROM finance_daily_closing_unlocks WHERE business_date=?", day);
            jdbc.update("DELETE FROM finance_daily_closing_access_locks WHERE business_date=?", day);
        }

        // All fixture journal entries and late postings are new in this
        // isolated test; remove them before dependent expense/transfer rows.
        for (UUID id : jdbc.query("SELECT id FROM finance_movements",
                (rs, row) -> rs.getObject(1, UUID.class))) {
            if (!originalMovements.contains(id)) jdbc.update("DELETE FROM finance_movements WHERE id=?", id);
        }
        for (UUID id : newExpenses) jdbc.update("DELETE FROM expenses WHERE id=?", id);
        for (UUID id : newTransfers) jdbc.update("DELETE FROM finance_transfers WHERE id=?", id);
        for (UUID id : newUsers) {
            jdbc.update("DELETE FROM security_user_roles WHERE user_id=?", id);
            jdbc.update("DELETE FROM app_users WHERE id=?", id);
        }
        for (var e : originalAccounts.entrySet()) {
            jdbc.update("UPDATE finance_accounts SET balance=?, opening_balance_initialized=? WHERE code=?",
                    e.getValue().balance(), e.getValue().initialized(), e.getKey());
        }
    }
}
