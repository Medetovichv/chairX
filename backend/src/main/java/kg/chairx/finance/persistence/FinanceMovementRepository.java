package kg.chairx.finance.persistence;

import kg.chairx.finance.domain.FinanceAccount;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.Types;
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
        insert(id, account, amount, movementType, sourceType, sourceId,
                actor, null, "POSTING_DATE");
    }

    /**
     * Explicit businessDate is only for controlled historical adjustments.
     * Posting timestamp always comes from the actual database clock.
     */
    public void insert(
            UUID id,
            FinanceAccount account,
            BigDecimal amount,
            String movementType,
            String sourceType,
            UUID sourceId,
            String actor,
            LocalDate businessDate,
            String businessDateSource
    ) {
        if (businessDateSource == null || !(businessDateSource.equals("POSTING_DATE")
                || businessDateSource.equals("HISTORICAL_CORRECTION"))) {
            throw new IllegalArgumentException("Unknown business-date source");
        }
        int inserted = jdbc.sql("""
                INSERT INTO finance_movements (
                    id,
                    account_code,
                    amount,
                    movement_type,
                    source_type,
                    source_id,
                    created_by, created_at, business_date, business_date_source
                )
                VALUES (
                    :id,
                    :account,
                    :amount,
                    :movementType,
                    :sourceType,
                    :sourceId,
                    :actor, clock_timestamp(),
                    COALESCE(:businessDate, (clock_timestamp() AT TIME ZONE 'Asia/Bishkek')::date),
                    :businessDateSource
                )
                """)
                .param("id", id)
                .param("account", account.name())
                .param("amount", amount)
                .param("movementType", movementType)
                .param("sourceType", sourceType)
                .param("sourceId", sourceId)
                .param("actor", actor)
                .param("businessDate", businessDate, Types.DATE)
                .param("businessDateSource", businessDateSource)
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