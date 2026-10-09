package kg.chairx.finance.persistence;

import kg.chairx.finance.domain.FinanceAccount;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class FinanceTransferRepository {

    private final JdbcClient jdbc;

    public FinanceTransferRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TransferRecord> findById(UUID id) {
        return jdbc.sql("""
                SELECT id, from_account, to_account, amount, created_by
                FROM finance_transfers
                WHERE id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> new TransferRecord(
                        rs.getObject("id", UUID.class),
                        FinanceAccount.valueOf(rs.getString("from_account")),
                        FinanceAccount.valueOf(rs.getString("to_account")),
                        rs.getBigDecimal("amount"),
                        rs.getString("created_by")
                ))
                .optional();
    }

    public boolean insertIfAbsent(
            UUID id,
            FinanceAccount from,
            FinanceAccount to,
            BigDecimal amount,
            String actor
    ) {
        int inserted = jdbc.sql("""
                INSERT INTO finance_transfers (
                    id, from_account, to_account, amount, created_by
                )
                VALUES (
                    :id, :fromAccount, :toAccount, :amount, :actor
                )
                ON CONFLICT (id) DO NOTHING
                """)
                .param("id", id)
                .param("fromAccount", from.name())
                .param("toAccount", to.name())
                .param("amount", amount)
                .param("actor", actor)
                .update();

        return inserted == 1;
    }

    public record TransferRecord(
            UUID id,
            FinanceAccount from,
            FinanceAccount to,
            BigDecimal amount,
            String actor
    ) {}
}