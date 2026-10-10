package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class FinanceApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        assertThat(jdbc.queryForObject("select current_database()", String.class))
                .isEqualTo("chairx_test");
        jdbc.update("delete from finance_movements");
        jdbc.update("delete from finance_transfers");
        jdbc.update("delete from finance_daily_closing_accounts");
        jdbc.update("delete from finance_daily_closings");
        jdbc.update("update finance_accounts set balance=0, opening_balance_initialized=false");
    }

    @Test
    void openingBalanceEndpointInitializesOnceWithoutDuplicatePosting() throws Exception {
        String json = "{\"account\":\"CASH\",\"amount\":100000}";
        mvc.perform(post("/api/finance/opening-balances")
                        .with(user("accountant").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "FINANCE_INITIALIZE")))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CASH"))
                .andExpect(jsonPath("$.initialized").value(true))
                .andExpect(jsonPath("$.balance").value(100000));
        mvc.perform(post("/api/finance/opening-balances")
                        .with(user("accountant").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "FINANCE_INITIALIZE")))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
        assertThat(countMovements("OPENING_BALANCE")).isEqualTo(1);
        assertThat(balance("CASH")).isEqualByComparingTo("100000");
    }

    @Test
    void openingBalanceAcceptsZeroWithoutFakeJournalEntry() throws Exception {
        mvc.perform(post("/api/finance/opening-balances")
                        .with(user("accountant").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "FINANCE_INITIALIZE")))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"BANK\",\"amount\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized").value(true));
        assertThat(countMovements("OPENING_BALANCE")).isZero();
        assertThat(balance("BANK")).isEqualByComparingTo("0");
    }

    @Test
    void transferEndpointMovesMoneyOnceAndRejectsChangedPayload() throws Exception {
        initialize(10000, 20000);
        UUID id = UUID.randomUUID();
        String json = transferJson(id, "CASH", "BANK", 3000);
        mvc.perform(post("/api/finance/transfers").with(authority("FINANCE_TRANSFER", "cashier"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(id.toString()));

        // Retries by another authorized employee are still idempotent.
        mvc.perform(post("/api/finance/transfers").with(authority("FINANCE_TRANSFER", "manager"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
        mvc.perform(post("/api/finance/transfers").with(authority("FINANCE_TRANSFER", "cashier"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(id, "CASH", "BANK", 3500)))
                .andExpect(status().isConflict());

        assertThat(balance("CASH")).isEqualByComparingTo("7000");
        assertThat(balance("BANK")).isEqualByComparingTo("23000");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_movements WHERE source_type='TRANSFER' AND source_id=?",
                Long.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT SUM(amount) FROM finance_movements WHERE source_type='TRANSFER' AND source_id=?",
                BigDecimal.class, id)).isEqualByComparingTo("0");
    }

    @Test
    void financeWritesRequireTheirDedicatedPermissions() throws Exception {
        mvc.perform(post("/api/finance/opening-balances").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"CASH\",\"amount\":100}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/finance/transfers").with(authority("FINANCE_READ", "viewer"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(UUID.randomUUID(), "CASH", "BANK", 100)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from finance_movements", Long.class))
                .isZero();
    }

    @Test
    void invalidAccountAndFractionalTransferAreRejected() throws Exception {
        initialize(1000, 1000);
        mvc.perform(post("/api/finance/transfers").with(authority("FINANCE_TRANSFER", "cashier"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transferId\":\"" + UUID.randomUUID() + "\",\"from\":\"CASH\","
                                + "\"to\":\"CASH\",\"amount\":10}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/transfers").with(authority("FINANCE_TRANSFER", "cashier"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transferId\":\"" + UUID.randomUUID() + "\",\"from\":\"BANK\","
                                + "\"to\":\"CASH\",\"amount\":2.5}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_movements WHERE source_type='TRANSFER'", Long.class))
                .isZero();
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(
            String permission, String actor) {
        return user(actor).authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(permission));
    }

    private void initialize(long cash, long bank) {
        // Setup only: endpoint itself is tested separately above.
        jdbc.update("UPDATE finance_accounts SET balance=?, opening_balance_initialized=true WHERE code='CASH'", cash);
        jdbc.update("UPDATE finance_accounts SET balance=?, opening_balance_initialized=true WHERE code='BANK'", bank);
    }

    private long countMovements(String type) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM finance_movements WHERE movement_type=?", Long.class, type);
    }

    private BigDecimal balance(String account) {
        return jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code=?", BigDecimal.class, account);
    }

    private String transferJson(UUID id, String from, String to, long amount) {
        return "{\"transferId\":\"" + id + "\",\"from\":\"" + from + "\",\"to\":\""
                + to + "\",\"amount\":" + amount + "}";
    }
}
