package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.finance.application.FinanceOpeningBalanceService;
import kg.chairx.finance.domain.FinanceAccount;
import kg.chairx.finance.persistence.FinanceAccountRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class FinanceOpeningBalanceIntegrationTest {

    @Autowired
    FinanceOpeningBalanceService service;

    @Autowired
    FinanceAccountRepository accounts;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    @BeforeEach
    void reset() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()", String.class
        )).isEqualTo("chairx_test");

        jdbc.update("DELETE FROM finance_movements");

        jdbc.update("""
                UPDATE finance_accounts
                SET balance = 0,
                    opening_balance_initialized = FALSE
                """);
    }

    @Test
    void initializesPositiveOpeningBalance() {
        service.initialize(
                FinanceAccount.CASH,
                new BigDecimal("50000"),
                "admin"
        );

        BigDecimal balance = transactions.execute(
                status -> accounts.balance(FinanceAccount.CASH)
        );

        assertThat(balance).isEqualByComparingTo("50000");

        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM finance_movements
                WHERE account_code = 'CASH'
                  AND movement_type = 'OPENING_BALANCE'
                """, Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void initializesZeroOpeningBalanceWithoutFakeMovement() {
        service.initialize(
                FinanceAccount.BANK,
                BigDecimal.ZERO,
                "admin"
        );

        Boolean initialized = jdbc.queryForObject("""
                SELECT opening_balance_initialized
                FROM finance_accounts
                WHERE code = 'BANK'
                """, Boolean.class);

        assertThat(initialized).isTrue();

        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM finance_movements
                WHERE account_code = 'BANK'
                """, Integer.class);

        assertThat(count).isZero();
    }

    @Test
    void rejectsRepeatedInitialization() {
        service.initialize(
                FinanceAccount.CASH,
                new BigDecimal("50000"),
                "admin"
        );

        assertThatThrownBy(() ->
                service.initialize(
                        FinanceAccount.CASH,
                        new BigDecimal("30000"),
                        "admin"
                )
        ).isInstanceOf(IllegalStateException.class);

        BigDecimal balance = transactions.execute(
                status -> accounts.balance(FinanceAccount.CASH)
        );

        assertThat(balance).isEqualByComparingTo("50000");
    }

    @Test
    void rejectsFractionalAmounts() {
        assertThatThrownBy(() ->
                service.initialize(
                        FinanceAccount.CASH,
                        new BigDecimal("100.50"),
                        "admin"
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() ->
                service.initialize(
                        FinanceAccount.CASH,
                        new BigDecimal("-100"),
                        "admin"
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}