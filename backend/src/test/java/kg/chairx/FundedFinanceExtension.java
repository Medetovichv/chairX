package kg.chairx;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import org.junit.jupiter.api.extension.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicit finance fixture for existing document workflow tests, restored after each test. */
public class FundedFinanceExtension implements BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(FundedFinanceExtension.class);
    private record Account(String code, BigDecimal balance, boolean initialized) {}
    private record Snapshot(List<Account> accounts, List<UUID> movements) {}
    private JdbcTemplate jdbc(ExtensionContext context) {
        JdbcTemplate jdbc = SpringExtension.getApplicationContext(context).getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        return jdbc;
    }
    public void beforeEach(ExtensionContext context) {
        JdbcTemplate jdbc = jdbc(context);
        var accounts = jdbc.query("SELECT code, balance, opening_balance_initialized FROM finance_accounts",
                (rs, row) -> new Account(rs.getString(1), rs.getBigDecimal(2), rs.getBoolean(3)));
        var movements = jdbc.query("SELECT id FROM finance_movements", (rs, row) -> rs.getObject(1, UUID.class));
        context.getStore(NS).put("snapshot", new Snapshot(accounts, movements));
        jdbc.update("UPDATE finance_accounts SET balance = 1000000, opening_balance_initialized = TRUE");
    }
    public void afterEach(ExtensionContext context) {
        JdbcTemplate jdbc = jdbc(context);
        Snapshot snapshot = context.getStore(NS).remove("snapshot", Snapshot.class);
        if (snapshot == null) return;
        var generated = jdbc.query("SELECT id FROM finance_movements", (rs, row) -> rs.getObject(1, UUID.class));
        for (UUID id : generated) {
            if (!snapshot.movements().contains(id)) jdbc.update("DELETE FROM finance_movements WHERE id = ?", id);
        }
        for (Account account : snapshot.accounts()) {
            jdbc.update("UPDATE finance_accounts SET balance = ?, opening_balance_initialized = ? WHERE code = ?",
                    account.balance(), account.initialized(), account.code());
        }
    }
}
