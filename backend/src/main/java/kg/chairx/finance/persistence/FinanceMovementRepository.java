package kg.chairx.finance.persistence;

import kg.chairx.finance.domain.FinanceAccount;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class FinanceMovementRepository {

    private final JdbcClient jdbc;

    public FinanceMovementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(
            UUID id,
            FinanceAccount account,
            BigDecimal amount,
            String movementType,
            String sourceType,
            UUID sourceId,
            String actor
    ) {
        int inserted = jdbc.sql("""
                INSERT INTO finance_movements (
                    id,
                    account_code,
                    amount,
                    movement_type,
                    source_type,
                    source_id,
                    created_by
                )
                VALUES (
                    :id,
                    :account,
                    :amount,
                    :movementType,
                    :sourceType,
                    :sourceId,
                    :actor
                )
                """)
                .param("id", id)
                .param("account", account.name())
                .param("amount", amount)
                .param("movementType", movementType)
                .param("sourceType", sourceType)
                .param("sourceId", sourceId)
                .param("actor", actor)
                .update();

        if (inserted != 1) {
            throw new IllegalStateException(
                    "Не удалось сохранить финансовую операцию"
            );
        }
    }

    public boolean exists(
            String sourceType,
            UUID sourceId,
            FinanceAccount account
    ) {
        return Boolean.TRUE.equals(
                jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM finance_movements
                            WHERE source_type = :sourceType
                              AND source_id = :sourceId
                              AND account_code = :account
                        )
                        """)
                        .param("sourceType", sourceType)
                        .param("sourceId", sourceId)
                        .param("account", account.name())
                        .query(Boolean.class)
                        .single()
        );
    }
}