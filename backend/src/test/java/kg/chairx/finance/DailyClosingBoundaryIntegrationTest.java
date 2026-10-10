package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Backend boundary checks through REAL database-backed HTTP Basic RBAC,
 * not just a mocked AccessPolicy. Time advances without Thread.sleep().
 */
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, DailyClosingBoundaryIntegrationTest.ClockConfig.class})
class DailyClosingBoundaryIntegrationTest {
    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
    private static final LocalDate REPORT_DAY = LocalDate.of(2020, 10, 10);
    private static final UUID ADMIN_ROLE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ROLE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EMPLOYEE_ROLE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    static Instant at(int day, int hour, int minute, int second) {
        return LocalDateTime.of(2020, 10, day, hour, minute, second)
                .atZone(BISHKEK).toInstant();
    }

    static class AdjustableClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(at(11, 12, 59, 59));
        void set(Instant time) { current.set(time); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current.get(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean @Primary AdjustableClock testClock() { return new AdjustableClock(); }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdjustableClock clock;

    private final List<UUID> users = new ArrayList<>();
    private UUID closingId;
    private String employee;
    private String manager;
    private String admin;

    private String userWithRole(UUID role) {
        UUID id = UUID.randomUUID();
        users.add(id);
        String login = "p21_time_" + id;
        jdbc.update("""
                INSERT INTO app_users(id,username,password_hash,display_name,active)
                VALUES (?,?,?,'P21 time test',TRUE)
                """, id, login, PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .encode("test-password"));
        jdbc.update("INSERT INTO security_user_roles(user_id,role_id) VALUES (?,?)", id, role);
        return login;
    }

    @BeforeEach
    void createReport() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        clock.set(at(11, 12, 59, 59));
        employee = userWithRole(EMPLOYEE_ROLE);
        manager = userWithRole(MANAGER_ROLE);
        admin = userWithRole(ADMIN_ROLE);
        closingId = UUID.randomUUID();
        jdbc.update("INSERT INTO finance_daily_closings(id,business_date,created_by) VALUES (?,?,?)",
                closingId, REPORT_DAY, "p21-test");
        jdbc.update("""
                INSERT INTO finance_daily_closing_accounts
                   (closing_id,account_code,expected_balance,actual_balance)
                VALUES (?,'CASH',0,0),(?,'BANK',0,0)
                """, closingId, closingId);
    }

    private String url() {
        return "/api/finance/closings/" + REPORT_DAY;
    }

    private void grantBy(String login) throws Exception {
        mvc.perform(post(url() + "/unlock").with(httpBasic(login, "test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Correct prior report\"}"))
                .andExpect(status().isOk());
    }

    private void updateAsEmployee(int version, int responseStatus) throws Exception {
        mvc.perform(put(url()).with(httpBasic(employee, "test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%d,"actualCash":0,"actualBank":0,
                                 "reason":"Correct actual count"}
                                """.formatted(version)))
                .andExpect(status().is(responseStatus));
    }

    @Test
    void exact1300CutoffExpiryAndAdminOverride() throws Exception {
        // 12:59:59 of next calendar day is open without manager intervention.
        mvc.perform(get(url() + "/access").with(httpBasic(employee, "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(true));
        updateAsEmployee(0, 200);

        // Exactly 13:00:00, yesterday is locked on the backend.
        clock.set(at(11, 13, 0, 0));
        mvc.perform(get(url() + "/access").with(httpBasic(employee, "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false));
        updateAsEmployee(1, 403);
        grantBy(manager);
        updateAsEmployee(1, 200);

        clock.set(at(11, 13, 59, 59));
        updateAsEmployee(2, 200);
        clock.set(at(11, 14, 0, 0));
        updateAsEmployee(3, 403);
        assertThat(jdbc.queryForObject("SELECT version FROM finance_daily_closings WHERE id=?",
                Long.class, closingId)).isEqualTo(3);
    }

    @Test
    void managerGrantOnFifthDayEndsAtSixthMidnightAndAdminCanReopen() throws Exception {
        clock.set(at(15, 23, 30, 0));
        grantBy(manager);
        Instant expiry = jdbc.queryForObject("""
                SELECT expires_at FROM finance_daily_closing_unlocks
                WHERE business_date=? ORDER BY unlocked_at DESC LIMIT 1
                """, (rs, row) -> rs.getTimestamp(1).toInstant(), REPORT_DAY);
        assertThat(expiry).isEqualTo(at(16, 0, 0, 0));
        clock.set(at(15, 23, 59, 59));
        updateAsEmployee(0, 200);

        clock.set(at(16, 0, 0, 0));
        updateAsEmployee(1, 403);
        mvc.perform(post(url() + "/unlock").with(httpBasic(manager, "test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Too late for manager\"}"))
                .andExpect(status().isForbidden());
        grantBy(admin);
        updateAsEmployee(1, 200);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM finance_daily_closing_unlocks WHERE business_date=?
                """, Long.class, REPORT_DAY)).isEqualTo(2);
    }

    @AfterEach
    void cleanup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        if (closingId != null) {
            jdbc.update("DELETE FROM audit_entries WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?",
                    closingId);
            jdbc.update("DELETE FROM finance_daily_closing_accounts WHERE closing_id=?", closingId);
            jdbc.update("DELETE FROM finance_daily_closings WHERE id=?", closingId);
        }
        UUID provisional = UUID.nameUUIDFromBytes(
                ("FINANCE_DAILY_CLOSING:" + REPORT_DAY)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbc.update("DELETE FROM audit_entries WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?",
                provisional);
        jdbc.update("DELETE FROM finance_daily_closing_unlocks WHERE business_date=?", REPORT_DAY);
        jdbc.update("DELETE FROM finance_daily_closing_access_locks WHERE business_date=?", REPORT_DAY);
        for (UUID id : users) {
            jdbc.update("DELETE FROM security_user_roles WHERE user_id=?", id);
            jdbc.update("DELETE FROM app_users WHERE id=?", id);
        }
    }
}
