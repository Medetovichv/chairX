package kg.chairx.exchange.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ExchangeSettlementRepository {

    private final JdbcTemplate jdbc;

    public ExchangeSettlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean tryInsert(
            UUID id,
            UUID exchangeId,
            String direction,
            String method,
            BigDecimal amount,
            String reference,
            UUID idempotencyKey,
            String requestFingerprint,
            String createdBy
    ) {
        int inserted = jdbc.update("""
                INSERT INTO exchange_settlements (
                    id,
                    exchange_id,
                    direction,
                    method,
                    amount,
                    reference,
                    idempotency_key,
                    request_fingerprint,
                    created_by,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                id,
                exchangeId,
                direction,
                method,
                amount,
                reference,
                idempotencyKey,
                requestFingerprint,
                createdBy
        );

        return inserted == 1;
    }

    public Optional<Settlement> findByIdempotencyKey(UUID key) {
        return jdbc.query("""
                SELECT *
                FROM exchange_settlements
                WHERE idempotency_key = ?
                """,
                (rs, rowNum) -> new Settlement(
                        rs.getObject("id", UUID.class),
                        rs.getObject("exchange_id", UUID.class),
                        rs.getString("direction"),
                        rs.getString("method"),
                        rs.getBigDecimal("amount"),
                        rs.getString("reference"),
                        rs.getObject("idempotency_key", UUID.class),
                        rs.getString("request_fingerprint"),
                        rs.getString("created_by"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                key
        ).stream().findFirst();
    }

    public List<Settlement> findByExchange(UUID exchangeId) {
        return jdbc.query("""
                SELECT *
                FROM exchange_settlements
                WHERE exchange_id = ?
                ORDER BY created_at, id
                """,
                (rs, rowNum) -> new Settlement(
                        rs.getObject("id", UUID.class),
                        rs.getObject("exchange_id", UUID.class),
                        rs.getString("direction"),
                        rs.getString("method"),
                        rs.getBigDecimal("amount"),
                        rs.getString("reference"),
                        rs.getObject("idempotency_key", UUID.class),
                        rs.getString("request_fingerprint"),
                        rs.getString("created_by"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                exchangeId
        );
    }

    public BigDecimal total(
            UUID exchangeId,
            String direction
    ) {
        BigDecimal result = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM exchange_settlements
                WHERE exchange_id = ?
                  AND direction = ?
                """,
                BigDecimal.class,
                exchangeId,
                direction
        );

        return result == null ? BigDecimal.ZERO : result;
    }

    public record Settlement(
            UUID id,
            UUID exchangeId,
            String direction,
            String method,
            BigDecimal amount,
            String reference,
            UUID idempotencyKey,
            String requestFingerprint,
            String createdBy,
            Instant createdAt
    ) {
    }
}