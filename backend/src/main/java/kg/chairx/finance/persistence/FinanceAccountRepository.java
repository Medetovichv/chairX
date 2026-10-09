package kg.chairx.finance.persistence;

import kg.chairx.finance.domain.FinanceAccount;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class FinanceAccountRepository {

    private final JdbcClient jdbc;

    public FinanceAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public BigDecimal balance(FinanceAccount account) {
        return jdbc.sql("""
                SELECT balance
                FROM finance_accounts
                WHERE code = :code
                """)
                .param("code", account.name())
                .query(BigDecimal.class)
                .single();
    }

    public BigDecimal lockBalance(FinanceAccount account) {
        return jdbc.sql("""
                SELECT balance
                FROM finance_accounts
                WHERE code = :code
                FOR UPDATE
                """)
                .param("code", account.name())
                .query(BigDecimal.class)
                .single();
    }

    public boolean isOpeningBalanceInitialized(FinanceAccount account) {
        return Boolean.TRUE.equals(
                jdbc.sql("""
                    SELECT opening_balance_initialized
                    FROM finance_accounts
                    WHERE code = :code
                    """)
                        .param("code", account.name())
                        .query(Boolean.class)
                        .single()
        );
    }

    public List<AccountSnapshot> findAllAccounts() {
        return jdbc.sql("""
            SELECT code, balance, opening_balance_initialized
            FROM finance_accounts
            ORDER BY CASE code
                WHEN 'CASH' THEN 1
                WHEN 'BANK' THEN 2
            END
            """)
                .query((rs, rowNum) -> new AccountSnapshot(
                        FinanceAccount.valueOf(rs.getString("code")),
                        rs.getBigDecimal("balance"),
                        rs.getBoolean("opening_balance_initialized")
                ))
                .list();
    }

    public record AccountSnapshot(
            FinanceAccount account,
            BigDecimal balance,
            boolean initialized
    ) {}

    public void markOpeningBalanceInitialized(FinanceAccount account) {
        int updated = jdbc.sql("""
            UPDATE finance_accounts
            SET opening_balance_initialized = TRUE
            WHERE code = :code
              AND opening_balance_initialized = FALSE
            """)
                .param("code", account.name())
                .update();

        if (updated != 1) {
            throw new IllegalStateException(
                    "Начальный остаток уже зарегистрирован"
            );
        }
    }

    public void changeBalance(
            FinanceAccount account,
            BigDecimal amount
    ) {
        int updated = jdbc.sql("""
                UPDATE finance_accounts
                SET balance = balance + :amount
                WHERE code = :code
                  AND balance + :amount >= 0
                  AND NOT EXISTS (
                      SELECT 1 FROM finance_daily_closings
                      WHERE business_date = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Bishkek')::date
                  )
                """)
                .param("code", account.name())
                .param("amount", amount)
                .update();

        if (updated != 1) {
            throw new IllegalStateException(
                    "Недостаточно средств или счёт не существует"
            );
        }
    }
}