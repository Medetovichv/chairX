package kg.chairx.exchange.infrastructure;

import kg.chairx.exchange.domain.Exchange;
import kg.chairx.exchange.domain.ExchangeStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ExchangeRepository {

    private final JdbcTemplate jdbc;

    public ExchangeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Exchange> MAPPER =
            (rs, rowNum) -> map(rs);

    public boolean tryInsert(Exchange exchange) {
        try {
            int inserted = jdbc.update("""
                    INSERT INTO exchanges (
                        id,
                        original_sale_id,
                        new_sale_id,
                        return_id,
                        status,
                        returned_value,
                        new_sale_total,
                        credit_applied,
                        additional_payment_due,
                        refund_due,
                        idempotency_key,
                        request_fingerprint,
                        created_by,
                        created_at,
                        completed_by,
                        completed_at
                    )
                    VALUES (
                        ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?
                    )
                    ON CONFLICT (idempotency_key) DO NOTHING
                    """,
                    exchange.id(),
                    exchange.originalSaleId(),
                    exchange.newSaleId(),
                    exchange.returnId(),
                    exchange.status().name(),
                    exchange.returnedValue(),
                    exchange.newSaleTotal(),
                    exchange.creditApplied(),
                    exchange.additionalPaymentDue(),
                    exchange.refundDue(),
                    exchange.idempotencyKey(),
                    exchange.requestFingerprint(),
                    exchange.createdBy(),
                    java.sql.Timestamp.from(exchange.createdAt()),
                    exchange.completedBy(),
                    exchange.completedAt() == null
                            ? null
                            : java.sql.Timestamp.from(exchange.completedAt())
            );

            return inserted == 1;
        } catch (DuplicateKeyException exception) {
            throw exception;
        }
    }

    public Optional<Exchange> find(UUID exchangeId) {
        return jdbc.query("""
                        SELECT *
                        FROM exchanges
                        WHERE id = ?
                        """,
                MAPPER,
                exchangeId
        ).stream().findFirst();
    }

    public Optional<Exchange> findByIdempotencyKey(UUID key) {
        return jdbc.query("""
                        SELECT *
                        FROM exchanges
                        WHERE idempotency_key = ?
                        """,
                MAPPER,
                key
        ).stream().findFirst();
    }

    public Optional<Exchange> lock(UUID exchangeId) {
        return jdbc.query("""
                        SELECT *
                        FROM exchanges
                        WHERE id = ?
                        FOR UPDATE
                        """,
                MAPPER,
                exchangeId
        ).stream().findFirst();
    }

    public boolean existsByReturn(UUID returnId) {
        Boolean exists = jdbc.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM exchanges
                            WHERE return_id = ?
                        )
                        """,
                Boolean.class,
                returnId
        );

        return Boolean.TRUE.equals(exists);
    }

    public boolean existsByNewSale(UUID saleId) {
        Boolean exists = jdbc.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM exchanges
                            WHERE new_sale_id = ?
                        )
                        """,
                Boolean.class,
                saleId
        );

        return Boolean.TRUE.equals(exists);
    }

    public int complete(
            UUID exchangeId,
            String completedBy
    ) {
        return jdbc.update("""
                        UPDATE exchanges
                        SET status = 'COMPLETED',
                            completed_by = ?,
                            completed_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                          AND status = 'PENDING_SETTLEMENT'
                        """,
                completedBy,
                exchangeId
        );
    }

    private static Exchange map(ResultSet rs) throws SQLException {
        var completedAt = rs.getTimestamp("completed_at");

        return new Exchange(
                rs.getObject("id", UUID.class),
                rs.getObject("original_sale_id", UUID.class),
                rs.getObject("new_sale_id", UUID.class),
                rs.getObject("return_id", UUID.class),
                ExchangeStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("returned_value"),
                rs.getBigDecimal("new_sale_total"),
                rs.getBigDecimal("credit_applied"),
                rs.getBigDecimal("additional_payment_due"),
                rs.getBigDecimal("refund_due"),
                rs.getObject("idempotency_key", UUID.class),
                rs.getString("request_fingerprint"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("completed_by"),
                completedAt == null ? null : completedAt.toInstant()
        );
    }
}