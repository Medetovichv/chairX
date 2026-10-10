package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.finance.application.FinanceOpeningBalanceService;
import kg.chairx.finance.application.FinanceTransferService;
import kg.chairx.finance.domain.FinanceAccount;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import kg.chairx.finance.domain.InsufficientFundsException;
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
class FinanceTransferIntegrationTest {

    @Autowired FinanceTransferService transfers;
    @Autowired FinanceOpeningBalanceService openingBalances;
    @Autowired JdbcTemplate jdbc;

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
    }

    @Test
    void concurrentRequestsWithSameIdTransferOnlyOnce() throws Exception {
        UUID id = UUID.randomUUID();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);

            var first = executor.submit(() -> {
                start.await();
                return transfers.transfer(
                        id,
                        FinanceAccount.CASH,
                        FinanceAccount.BANK,
                        new BigDecimal("20000"),
                        "admin"
                );
            });

            var second = executor.submit(() -> {
                start.await();
                return transfers.transfer(
                        id,
                        FinanceAccount.CASH,
                        FinanceAccount.BANK,
                        new BigDecimal("20000"),
                        "admin"
                );
            });

            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(id);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(id);
        }

        assertThat(balance("CASH")).isEqualByComparingTo("30000");
        assertThat(balance("BANK")).isEqualByComparingTo("120000");
        assertThat(movementCount(id)).isEqualTo(2);
    }

    @Test
    void concurrentTransfersCannotOverdrawAccount() throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);

            var first = executor.submit(() -> {
                start.await();
                try {
                    transfers.transfer(
                            firstId,
                            FinanceAccount.CASH,
                            FinanceAccount.BANK,
                            new BigDecimal("40000"),
                            "admin"
                    );
                    return true;
                } catch (InsufficientFundsException e) {
                    return false;
                }
            });

            var second = executor.submit(() -> {
                start.await();
                try {
                    transfers.transfer(
                            secondId,
                            FinanceAccount.CASH,
                            FinanceAccount.BANK,
                            new BigDecimal("40000"),
                            "admin"
                    );
                    return true;
                } catch (InsufficientFundsException e) {
                    return false;
                }
            });

            start.countDown();

            boolean firstSucceeded = first.get(10, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(10, TimeUnit.SECONDS);

            // Ровно один перевод должен выполниться.
            assertThat(firstSucceeded ^ secondSucceeded).isTrue();
        }

        assertThat(balance("CASH")).isEqualByComparingTo("10000");
        assertThat(balance("BANK")).isEqualByComparingTo("140000");

        assertThat(movementCount(firstId) + movementCount(secondId))
                .isEqualTo(2);
    }

    @Test
    void concurrentOppositeTransfersDoNotDeadlock() throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);

            var first = executor.submit(() -> {
                start.await();
                return transfers.transfer(
                        firstId,
                        FinanceAccount.CASH,
                        FinanceAccount.BANK,
                        new BigDecimal("20000"),
                        "admin"
                );
            });

            var second = executor.submit(() -> {
                start.await();
                return transfers.transfer(
                        secondId,
                        FinanceAccount.BANK,
                        FinanceAccount.CASH,
                        new BigDecimal("10000"),
                        "admin"
                );
            });

            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(firstId);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(secondId);
        }

        // CASH: 50000 - 20000 + 10000 = 40000
        assertThat(balance("CASH")).isEqualByComparingTo("40000");

        // BANK: 100000 + 20000 - 10000 = 110000
        assertThat(balance("BANK")).isEqualByComparingTo("110000");

        // Каждый перевод создаёт две записи в журнале.
        assertThat(movementCount(firstId)).isEqualTo(2);
        assertThat(movementCount(secondId)).isEqualTo(2);

        // Каждый перевод сохраняет общую сумму денег.
        assertThat(movementSum(firstId)).isEqualByComparingTo("0");
        assertThat(movementSum(secondId)).isEqualByComparingTo("0");
    }

    @Test
    void transfersMoneyBetweenAccounts() {
        UUID id = UUID.randomUUID();

        transfers.transfer(
                id,
                FinanceAccount.CASH,
                FinanceAccount.BANK,
                new BigDecimal("20000"),
                "admin"
        );

        assertThat(balance("CASH")).isEqualByComparingTo("30000");
        assertThat(balance("BANK")).isEqualByComparingTo("120000");

        assertThat(movementCount(id)).isEqualTo(2);

        // Both journal legs must have exactly one effective date even when
        // the transfer crosses a business-day boundary during execution.
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT business_date)
                FROM finance_movements
                WHERE source_type = 'TRANSFER' AND source_id = ?
                """, Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM finance_movements
                WHERE source_type = 'TRANSFER' AND source_id = ?
                  AND business_date_source = 'POSTING_DATE'
                """, Integer.class, id)).isEqualTo(2);

        assertThat(movementSum(id))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void repeatedRequestDoesNotTransferMoneyTwice() {
        UUID id = UUID.randomUUID();

        for (int i = 0; i < 2; i++) {
            transfers.transfer(
                    id,
                    FinanceAccount.CASH,
                    FinanceAccount.BANK,
                    new BigDecimal("20000"),
                    "admin"
            );
        }

        assertThat(balance("CASH")).isEqualByComparingTo("30000");
        assertThat(balance("BANK")).isEqualByComparingTo("120000");
        assertThat(movementCount(id)).isEqualTo(2);
    }

    @Test
    void rejectsSameIdWithDifferentAmount() {
        UUID id = UUID.randomUUID();

        transfers.transfer(
                id,
                FinanceAccount.CASH,
                FinanceAccount.BANK,
                new BigDecimal("20000"),
                "admin"
        );

        assertThatThrownBy(() ->
                transfers.transfer(
                        id,
                        FinanceAccount.CASH,
                        FinanceAccount.BANK,
                        new BigDecimal("30000"),
                        "admin"
                )
        ).isInstanceOf(IllegalStateException.class);

        assertThat(balance("CASH")).isEqualByComparingTo("30000");
        assertThat(balance("BANK")).isEqualByComparingTo("120000");
    }

    @Test
    void insufficientFundsRollBackEntireTransfer() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() ->
                transfers.transfer(
                        id,
                        FinanceAccount.CASH,
                        FinanceAccount.BANK,
                        new BigDecimal("60000"),
                        "admin"
                )
        ).isInstanceOf(InsufficientFundsException.class);

        assertThat(balance("CASH")).isEqualByComparingTo("50000");
        assertThat(balance("BANK")).isEqualByComparingTo("100000");

        assertThat(movementCount(id)).isZero();

        Integer transferCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM finance_transfers WHERE id = ?",
                Integer.class,
                id
        );

        assertThat(transferCount).isZero();
    }

    @Test
    void rejectsFractionalTransfer() {
        assertThatThrownBy(() ->
                transfers.transfer(
                        UUID.randomUUID(),
                        FinanceAccount.CASH,
                        FinanceAccount.BANK,
                        new BigDecimal("100.50"),
                        "admin"
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private BigDecimal balance(String account) {
        return jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code = ?",
                BigDecimal.class,
                account
        );
    }

    private Integer movementCount(UUID transferId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM finance_movements
                WHERE source_type = 'TRANSFER'
                  AND source_id = ?
                """, Integer.class, transferId);
    }

    private BigDecimal movementSum(UUID transferId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM finance_movements
                WHERE source_type = 'TRANSFER'
                  AND source_id = ?
                """, BigDecimal.class, transferId);
    }
}