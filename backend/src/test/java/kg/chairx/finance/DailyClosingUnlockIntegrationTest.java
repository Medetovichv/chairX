package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class DailyClosingUnlockIntegrationTest {
    private static final UUID ADMIN_ROLE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ROLE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EMPLOYEE_ROLE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final List<UUID> createdUsers = new ArrayList<>();
    private UUID closingId;
    private LocalDate reportDate;

    private String newUser(UUID role) {
        UUID id = UUID.randomUUID();
        createdUsers.add(id);
        String username = "p21_" + id;
        jdbc.update("""
                INSERT INTO app_users (id, username, password_hash, display_name, active)
                VALUES (?, ?, ?, ?, TRUE)
                """, id, username, PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .encode("p21-password"), "P21 Test");
        jdbc.update("INSERT INTO security_user_roles (user_id, role_id) VALUES (?, ?)", id, role);
        return username;
    }

    private void insertOldClosing() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        reportDate = LocalDate.now(ZoneId.of("Asia/Bishkek")).minusDays(2);
        closingId = UUID.randomUUID();
        jdbc.update("INSERT INTO finance_daily_closings (id, business_date, created_by) VALUES (?, ?, ?)",
                closingId, reportDate, "integration");
        jdbc.update("""
                INSERT INTO finance_daily_closing_accounts
                    (closing_id, account_code, expected_balance, actual_balance)
                VALUES (?, 'CASH', 0, 0), (?, 'BANK', 0, 0)
                """, closingId, closingId);
    }

    @AfterEach
    void clearTestData() {
        if (reportDate != null) {
            if (closingId != null) {
                jdbc.update("DELETE FROM audit_entries WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?",
                        closingId);
                jdbc.update("DELETE FROM finance_daily_closing_accounts WHERE closing_id=?", closingId);
                jdbc.update("DELETE FROM finance_daily_closings WHERE id=?", closingId);
            }
            UUID pendingAuditId = UUID.nameUUIDFromBytes(("FINANCE_DAILY_CLOSING:" + reportDate)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jdbc.update("DELETE FROM audit_entries WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?",
                    pendingAuditId);
            jdbc.update("DELETE FROM finance_daily_closing_unlocks WHERE business_date=?", reportDate);
            jdbc.update("DELETE FROM finance_daily_closing_access_locks WHERE business_date=?", reportDate);
        }
        for (UUID id : createdUsers) {
            jdbc.update("DELETE FROM security_user_roles WHERE user_id=?", id);
            jdbc.update("DELETE FROM app_users WHERE id=?", id);
        }
        createdUsers.clear();
    }

    @Test
    void managerUnlockAllowsEmployeeEditAndAdminCanRevokeIt() throws Exception {
        insertOldClosing();
        String employee = newUser(EMPLOYEE_ROLE);
        String manager = newUser(MANAGER_ROLE);
        String admin = newUser(ADMIN_ROLE);
        String url = "/api/finance/closings/" + reportDate;

        String body = """
                {"expectedVersion":0,"actualCash":0,"actualBank":0,"reason":"Correct count"}
                """;
        mvc.perform(put(url).with(httpBasic(employee, "p21-password")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT version FROM finance_daily_closings WHERE id=?",
                Long.class, closingId)).isZero();

        mvc.perform(post(url + "/unlock")
                        .with(httpBasic(employee, "p21-password")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Need to correct count\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post(url + "/unlock")
                        .with(httpBasic(manager, "p21-password")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Need to correct count\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").exists());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_daily_closing_unlocks WHERE business_date=?",
                Integer.class, reportDate)).isEqualTo(1);

        mvc.perform(put(url).with(httpBasic(employee, "p21-password")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(put(url).with(httpBasic(employee, "p21-password")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT version FROM finance_daily_closings WHERE id=?",
                Long.class, closingId)).isEqualTo(1L);

        mvc.perform(post(url + "/lock").with(httpBasic(admin, "p21-password")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(true));

        mvc.perform(put(url).with(httpBasic(employee, "p21-password")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"actualCash\":0,\"actualBank\":0,\"reason\":\"Again\"}"))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_entries WHERE entity_type='FINANCE_DAILY_CLOSING' AND entity_id=?",
                Integer.class, closingId)).isGreaterThanOrEqualTo(3);
    }

    @Test
    void catalogStillCannotReadOrUnlockDailyClosing() throws Exception {
        String url = "/api/finance/closings/" + LocalDate.now(ZoneId.of("Asia/Bishkek"));
        mvc.perform(get(url + "/access").with(httpBasic("catalog", "integration-test-password")))
                .andExpect(status().isForbidden());
        mvc.perform(post(url + "/unlock").with(httpBasic("catalog", "integration-test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Do not allow\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unlockRequestNeverExtendsAnExistingGrant() throws Exception {
        insertOldClosing();
        String manager = newUser(MANAGER_ROLE);
        String url = "/api/finance/closings/" + reportDate + "/unlock";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(url).with(httpBasic(manager, "p21-password")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Retried same grant\"}"))
                    .andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_daily_closing_unlocks WHERE business_date=?",
                Integer.class, reportDate)).isEqualTo(1);
    }
}
