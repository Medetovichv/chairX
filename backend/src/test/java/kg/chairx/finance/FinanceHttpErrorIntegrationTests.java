package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Deliberately NOT @Transactional: MockMvc exercises a real service
 * transaction. A rejected transfer must have rolled back before the
 * assertions in this test can see PostgreSQL.
 */
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
@AutoConfigureMockMvc
class FinanceHttpErrorIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private List<Account> original;

    private record Account(String code, BigDecimal balance, boolean initialized) {}

    @BeforeEach
    void before() {
        assertThat(jdbc.queryForObject("select current_database()", String.class))
                .isEqualTo("chairx_test");
        original = jdbc.query("""
                SELECT code, balance, opening_balance_initialized
                FROM finance_accounts ORDER BY code
                """, (rs, row) -> new Account(
                        rs.getString("code"),
                        rs.getBigDecimal("balance"),
                        rs.getBoolean("opening_balance_initialized")));
    }

    @AfterEach
    void after() {
        for (Account a : original) {
            jdbc.update("""
                    UPDATE finance_accounts
                    SET balance=?, opening_balance_initialized=?
                    WHERE code=?
                    """, a.balance(), a.initialized(), a.code());
        }
    }

    @Test
    void uninitializedAccountReturns409AndRollsBackTransferAndBothJournalEntries() throws Exception {
        jdbc.update("""
                UPDATE finance_accounts SET balance=1000,
                    opening_balance_initialized=true WHERE code='CASH'
                """);
        jdbc.update("""
                UPDATE finance_accounts SET balance=0,
                    opening_balance_initialized=false WHERE code='BANK'
                """);
        UUID id = UUID.randomUUID();
        BigDecimal cashBefore = balance("CASH");
        BigDecimal bankBefore = balance("BANK");

        mvc.perform(post("/api/finance/transfers")
                        .with(user("finance-operator").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "FINANCE_TRANSFER")))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transferId":"%s","from":"CASH","to":"BANK","amount":100}
                                """.formatted(id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCE_OPERATION_CONFLICT"));

        assertThat(balance("CASH")).isEqualByComparingTo(cashBefore);
        assertThat(balance("BANK")).isEqualByComparingTo(bankBefore);
        assertThat(jdbc.queryForObject(
                "select count(*) from finance_transfers where id=?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from finance_movements where source_type='TRANSFER' and source_id=?",
                Long.class, id)).isZero();
    }

    @Test
    void invalidCashFlowPeriodUses400InsteadOf500() throws Exception {
        for (String query : List.of(
                "?from=2026-10-20&to=2026-10-10",
                "?from=2026-10-10&to=2026-10-10")) {
            mvc.perform(get("/api/finance/cash-flow" + query)
                            .with(user("finance-reader").authorities(
                                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                            "FINANCE_READ"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    @Test
    void missingOrMalformedCashFlowDatesAreRejectedAndBishkekIntervalIsPreserved()
            throws Exception {
        var reader = user("finance-reader").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "FINANCE_READ"));

        mvc.perform(get("/api/finance/cash-flow?from=2026-10-01").with(reader))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/finance/cash-flow?from=bad&to=2026-10-20")
                        .with(user("finance-reader").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "FINANCE_READ"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/finance/cash-flow?from=2026-10-01&to=2026-11-01")
                        .with(user("finance-reader").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "FINANCE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-30T18:00:00Z"))
                .andExpect(jsonPath("$.to").value("2026-10-31T18:00:00Z"));
    }

    private BigDecimal balance(String code) {
        return jdbc.queryForObject(
                "select balance from finance_accounts where code=?",
                BigDecimal.class, code);
    }
}
