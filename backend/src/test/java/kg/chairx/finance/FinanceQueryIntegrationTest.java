package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.finance.application.FinanceOpeningBalanceService;
import kg.chairx.finance.application.FinanceQueryService;
import kg.chairx.finance.application.FinanceTransferService;
import kg.chairx.finance.domain.FinanceAccount;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class FinanceQueryIntegrationTest {

    @Autowired
    FinanceQueryService queries;

    @Autowired
    FinanceOpeningBalanceService openingBalances;

    @Autowired
    FinanceTransferService transfers;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()", String.class
        )).isEqualTo("chairx_test");

        jdbc.update("DELETE FROM finance_movements");
        jdbc.update("DELETE FROM finance_transfers");

        jdbc.update("""
                UPDATE finance_accounts
                SET balance = 0,
                    opening_balance_initialized = FALSE
                """);
    }

    @Test
    void returnsBothUninitializedAccounts() {
        var result = queries.getAccounts();

        assertThat(result).hasSize(2);

        assertThat(result)
                .extracting(account -> account.code())
                .containsExactly("CASH", "BANK");

        assertThat(result)
                .allSatisfy(account -> {
                    assertThat(account.balance())
                            .isEqualByComparingTo("0");

                    assertThat(account.initialized()).isFalse();
                });
    }

    @Test
    void returnsInitializedBalances() {
        openingBalances.initialize(
                FinanceAccount.CASH,
                new BigDecimal("50000"),
                "admin"
        );

        openingBalances.initialize(
                FinanceAccount.BANK,
                new BigDecimal("100000"),
                "admin"
        );

        var result = queries.getAccounts();

        assertThat(result).hasSize(2);

        assertThat(result.get(0).code()).isEqualTo("CASH");
        assertThat(result.get(0).balance())
                .isEqualByComparingTo("50000");
        assertThat(result.get(0).initialized()).isTrue();

        assertThat(result.get(1).code()).isEqualTo("BANK");
        assertThat(result.get(1).balance())
                .isEqualByComparingTo("100000");
        assertThat(result.get(1).initialized()).isTrue();
    }

    @Test
    void returnsUpdatedBalancesAfterTransfer() {
        openingBalances.initialize(
                FinanceAccount.CASH,
                new BigDecimal("50000"),
                "admin"
        );

        openingBalances.initialize(
                FinanceAccount.BANK,
                new BigDecimal("100000"),
                "admin"
        );

        transfers.transfer(
                UUID.randomUUID(),
                FinanceAccount.CASH,
                FinanceAccount.BANK,
                new BigDecimal("20000"),
                "admin"
        );

        var result = queries.getAccounts();

        assertThat(result.get(0).balance())
                .isEqualByComparingTo("30000");

        assertThat(result.get(1).balance())
                .isEqualByComparingTo("120000");
    }
}